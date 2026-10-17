package id.loclive.singleplayer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * R128 Singleplayer media bridge. A bounded in-world filled-map ItemDisplay
 * shows exactly the PNG rendered by LocLive Bridge (name, emoji, side sticker).
 * The map storage/packet layer is invoked reflectively because Mojang mapping
 * signatures vary across supported Fabric targets. Fail CLOSED to native text.
 * All calls occur on the integrated-server thread; no remote URLs or disk writes.
 */
public final class SingleplayerMapCards {
    @FunctionalInterface public interface Commands { int run(MinecraftServer server, String command); }
    public record MapResult(boolean ready, int mapId) {
        public static MapResult unavailable() { return new MapResult(false, -1); }
    }
    private static final int MAX_ACTIVE = 8;
    private static final int MAX_PNG_BYTES = 45000;
    private final ArrayDeque<Integer> free = new ArrayDeque<>();
    private final Map<Integer,Slot> slots = new HashMap<>();
    private int warned = 0;
    private record Slot(Object id, Object data) {}

    public void reset() { free.clear(); slots.clear(); warned=0; }
    public void release(int id) {
        if (slots.containsKey(id) && !free.contains(id)) free.addLast(id);
    }

    public MapResult attach(MinecraftServer server, ServerPlayer viewer, String encoded, String tag,
                            double x, double y, double z, Commands commands) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 60000 || server == null) return MapResult.unavailable();
        Integer acquired = null;
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            if (raw.length == 0 || raw.length > MAX_PNG_BYTES || !isPng(raw)) return MapResult.unavailable();
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(raw));
            if (source == null || source.getWidth() != 128 || source.getHeight() != 128) return MapResult.unavailable();
            byte[] colors = mapColors(source);
            Slot slot;
            if (!free.isEmpty()) {
                acquired = free.removeFirst();
                slot = slots.get(acquired);
            } else {
                if (slots.size() >= MAX_ACTIVE) return MapResult.unavailable();
                Object world = server.overworld();
                Object id = invokeNoArgs(world, "getFreeMapId");
                int numericalId = ((Number) invokeNoArgs(id, "id")).intValue();
                Object data = newMapData(world);
                storeMap(world, id, data);
                acquired = numericalId;
                slot = new Slot(id,data);
                slots.put(acquired,slot);
            }
            Field field = slot.data().getClass().getDeclaredField("colors");
            field.setAccessible(true);
            byte[] target = (byte[]) field.get(slot.data());
            if (target.length != colors.length) throw new IllegalStateException("unexpected map dimensions");
            System.arraycopy(colors,0,target,0,colors.length);
            invokeNoArgs(slot.data(), "setDirty");
            String loc = String.format(java.util.Locale.ROOT,"%.3f %.3f %.3f",x,y,z);
            // MapId is a Minecraft data component, NOT item legacy NBT.
            String nbt = "{Tags:[\"loclivesp\",\""+tag+"\"],billboard:\"center\",teleport_duration:3,"
                +"item_display:\"fixed\",transformation:{scale:[1.65f,1.65f,1.65f]},"
                +"item:{id:\"minecraft:filled_map\",count:1,components:{\"minecraft:map_id\":"+acquired+"}}}";
            // A rejected map packet must not leave a visible orphan ItemDisplay.
            sendMapPixels(server,slot,colors);
            if (commands.run(server,"summon minecraft:item_display "+loc+" "+nbt) < 1)
                throw new IllegalStateException("item display command rejected");
            return new MapResult(true,acquired);
        } catch (ReflectiveOperationException | RuntimeException | java.io.IOException ex) {
            if (acquired != null) release(acquired);
            if (warned++ < 2) System.err.println("[LocLive SP] Chat color image unavailable; using normal text: "
                + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return MapResult.unavailable();
        }
    }

    private static boolean isPng(byte[] data) {
        byte[] signature={(byte)137,80,78,71,13,10,26,10};
        if (data.length < 64) return false;
        for (int i=0;i<signature.length;i++) if (data[i]!=signature[i]) return false;
        return true;
    }
    private static Object invokeNoArgs(Object object,String name) throws ReflectiveOperationException {
        Method method=object.getClass().getMethod(name);
        return method.invoke(object);
    }
    private static Object newMapData(Object world) throws ReflectiveOperationException {
        Class<?> klass=Class.forName("net.minecraft.world.level.saveddata.maps.MapItemSavedData");
        Object dimension=invokeNoArgs(world,"dimension");
        for (Method method:klass.getMethods()) {
            if (!method.getName().equals("createFresh") || method.getParameterCount()!=6) continue;
            return method.invoke(null,0.0,0.0,(byte)0,false,false,dimension);
        }
        throw new NoSuchMethodException("createFresh");
    }
    private static void storeMap(Object world,Object id,Object map) throws ReflectiveOperationException {
        for (Method method:world.getClass().getMethods()) {
            if (method.getName().equals("setMapData") && method.getParameterCount()==2) {
                method.invoke(world,id,map);return;
            }
        }
        throw new NoSuchMethodException("setMapData");
    }
    private static void sendMapPixels(MinecraftServer server,Slot slot,byte[] colors) throws ReflectiveOperationException {
        Class<?> patchClass=Class.forName("net.minecraft.world.level.saveddata.maps.MapItemSavedData$MapPatch");
        Constructor<?> patchCtor=patchClass.getDeclaredConstructor(int.class,int.class,int.class,int.class,byte[].class);
        Object patch=patchCtor.newInstance(0,0,128,128,colors.clone());
        Class<?> packetClass=Class.forName("net.minecraft.network.protocol.game.ClientboundMapItemDataPacket");
        Object packet=null;
        for (Constructor<?> ctor:packetClass.getConstructors()) {
            Class<?>[] types=ctor.getParameterTypes();
            if (types.length == 5 && types[0].isInstance(slot.id()) && types[1]==byte.class) {
                packet=ctor.newInstance(slot.id(),(byte)0,false,List.of(),Optional.of(patch));break;
            }
        }
        if (packet==null) throw new NoSuchMethodException("ClientboundMapItemDataPacket");
        for (ServerPlayer player:server.getPlayerList().getPlayers()) {
            Object listener=player.connection;
            boolean sent=false;
            for (Method method:listener.getClass().getMethods()) {
                if (method.getName().equals("send") && method.getParameterCount()==1
                    && method.getParameterTypes()[0].isInstance(packet)) {
                    method.invoke(listener,packet);sent=true;break;
                }
            }
            if (!sent) throw new NoSuchMethodException("send(map packet)");
        }
    }

    // Vanilla map's 4 brightness variants for representative map colors.
    // No runtime texture download or custom resource-pack requirement.
    private static final int[] BASE = {
        0x000000,0x7FB238,0xF7E9A3,0xC7C7C7,0xFF0000,0xA0A0FF,0xA7A7A7,0x007C00,0xFFFFFF,
        0xA4A8B8,0x976D4D,0x707070,0x4040FF,0x8F7748,0xFFFCF5,0xD87F33,0xB24CD8,
        0x6699D8,0xE5E533,0x7FCC19,0xF27FA5,0x4C4C4C,0x999999,0x4C7F99,0x7F3FB2,
        0x334CB2,0x664C33,0x667F33,0x993333,0x191919,0xFAEE4D,0x5CDBD5,0x4A80FF,
        0x00D93A,0x815631,0x700200,0xD1B1A1,0x9F5224,0x95576C,0x706C8A,0xBA8524,
        0x677535,0xA04D4E,0x392923,0x876B62,0x575C5C,0x7A4958,0x4C3E5C,0x4C3223,
        0x4C522A,0x8E3C2E,0x251610,0xBD3031,0x943F61,0x5C191D,0x167E86,0x3A8E8C,
        0x562C3E,0x14B485,0x646464,0xD8AF93,0x7FA796,0x9A5CC6,0x7F7F7F
    };
    private static final int[] SHADE = {180,220,255,135};
    private static byte[] mapColors(BufferedImage image) {
        byte[] palette = new byte[128*128];
        int[] rgb = new int[BASE.length*4];
        for (int i=0;i<BASE.length;i++) for (int j=0;j<4;j++) {
            int raw=BASE[i],mul=SHADE[j];
            rgb[i*4+j] = ((raw>>16&255)*mul/255<<16)|((raw>>8&255)*mul/255<<8)|((raw&255)*mul/255);
        }
        // 128*128*252 comparisons is bounded, and cards are rate-limited upstream.
        for (int y=0;y<128;y++) for (int x=0;x<128;x++) {
            int px=image.getRGB(x,y);
            int r=px>>16&255,g=px>>8&255,b=px&255;
            int best=4,score=Integer.MAX_VALUE;
            for (int k=4;k<rgb.length;k++) {
                int c=rgb[k]; int dr=r-(c>>16&255),dg=g-(c>>8&255),db=b-(c&255);
                int error=dr*dr*3 + dg*dg*4 + db*db*2;
                if (error<score) {score=error;best=k;}
            }
            palette[y*128+x]=(byte)best;
        }
        return palette;
    }
}
