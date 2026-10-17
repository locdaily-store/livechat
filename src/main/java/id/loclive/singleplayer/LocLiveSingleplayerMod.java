package id.loclive.singleplayer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/** R105-SP-PREVIEW: public Fabric Singleplayer with 3D smooth movement and custom Live Chat theme. */
public final class LocLiveSingleplayerMod implements ModInitializer {
    private final Queue<ChatHttpServer.Chat> incoming = new ConcurrentLinkedQueue<>();
    private final SingleplayerMapCards media = new SingleplayerMapCards();
    private final ArrayDeque<Box> boxes = new ArrayDeque<>();
    private final Map<String, Box> byTag = new HashMap<>();
    private ChatHttpServer http;
    private DirectTikTokService directTikTok;
    private String currentTikTokUser = "";
    private MinecraftServer activeServer;
    private long nextId;
    private int tick;
    private int appliedStyleVersion;
    // R125: recover displays saved by an interrupted stream or a missing helper mod.
    private int orphanSweepTick;

    private static final class Box {
        String tag;
        String dimension;
        long expires;
        String interaction;
        UUID holder;
        final double distance;
        final String viewer;
        final String message;
        LiveChatMotion.Position position;
        boolean mediaCard;
        int mediaMapId = -1;
        Box(String tag, String dimension, long expires, String interaction, double distance, LiveChatMotion.Position position, String viewer, String message) {
            this.tag=tag;
            this.dimension=dimension;
            this.expires=expires;
            this.interaction=interaction;
            this.distance=distance;
            this.viewer=viewer;
            this.message=message;
            this.position=position;
        }
    }

    @Override public void onInitialize() {
        INSTANCE = this;
        OwnerSettings.load(FabricLoader.getInstance().getConfigDir());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Do not bind the bridge in a separate dedicated server.
            if (server.isDedicatedServer()) return;
            // Orphaned TextDisplay/Interaction entities can survive save/reload after a
            // crash, even though their Java Box/timer no longer exists. Purge them
            // before reading config: a missing/invalid config must not skip cleanup.
            activeServer = server;
            purgeAbandonedChatEntities(server);
            media.reset();
            orphanSweepTick = 0;
            startDirectTikTok();
            try {
                Path config = FabricLoader.getInstance().getConfigDir().resolve("loclive-singleplayer.properties");
                Properties props = new Properties();
                if (!Files.isRegularFile(config)) {
                    System.err.println("[LocLive SP] Set up " + config + " with the installer first.");
                    return;
                }
                try (java.io.Reader reader=Files.newBufferedReader(config,java.nio.charset.StandardCharsets.UTF_8)) { props.load(reader); }
                String secret = props.getProperty("secret","").strip();
                int port = Integer.parseInt(props.getProperty("port","8765"));
                http = new ChatHttpServer("127.0.0.1",port,secret, incoming::add);
                http.restoreState(Boolean.parseBoolean(props.getProperty("lc_installed","true")),
                    Boolean.parseBoolean(props.getProperty("lc_enabled","true")), LiveChatAppearance.fromProperties(props));
                appliedStyleVersion=http.styleVersion();
                http.start();
                System.out.println("[LocLive SP] Live Chat HTTP listening on 127.0.0.1:" + port);
            } catch (Exception ex) {
                System.err.println("[LocLive SP] Could not bind Live Chat (port or config): " + ex.getMessage());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Run before world saving starts; otherwise command entities can persist.
            if (!server.isDedicatedServer()) {
                for (Box box : new ArrayList<>(boxes)) remove(server, box);
                purgeAbandonedChatEntities(server);
                incoming.clear();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (http != null) { http.close(); http=null; }
            if (directTikTok != null) { directTikTok.close(); directTikTok=null; }
            // Cleanup is unconditional even if loading configuration/HTTP failed.
            purgeAbandonedChatEntities(server);
            boxes.clear(); byTag.clear(); incoming.clear(); media.reset(); activeServer=null;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server != activeServer) return;
            if (http != null && http.takeClearRequested()) {
                for (Box box : new ArrayList<>(boxes)) remove(server,box);
                incoming.clear();
                // Also clear orphan entities that are not in the in-memory registry.
                purgeAbandonedChatEntities(server);
            }
            // A previous stream's displays can load with chunks after SERVER_STARTED.
            // Sweep when no active box exists; never erase a currently active chat.
            if (++orphanSweepTick >= 100) {
                orphanSweepTick = 0;
                if (boxes.isEmpty()) purgeAbandonedChatEntities(server);
            }
            if (http != null && appliedStyleVersion != http.styleVersion()) {
                appliedStyleVersion = http.styleVersion();
                for (Box box : new ArrayList<>(boxes)) {
                    if (box.mediaCard) continue; // Rich card already carries colors/text/sticker.
                    LiveChatAppearance style = http.appearance();
                    execute(server,"data merge entity @e[type=minecraft:text_display,tag="+box.tag+",limit=1] {"
                        +style.displayFields(box.viewer,box.message)+"}");
                    execute(server,"data merge entity @e[type=minecraft:interaction,tag="+box.tag+",limit=1] "
                        +hitboxNbt(style));
                }
            }
            int processed=0;
            ChatHttpServer.Chat item;
            while (processed++ < 4 && (item=incoming.poll()) != null) spawn(server,item);
            // Follow every server tick (20 Hz), instead of snapping every other tick.
            update(server);
        });

        AttackEntityCallback.EVENT.register((player,level,hand,entity,hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            Box box = find(entity.entityTags());
            if (box == null || "display".equals(box.interaction)) return InteractionResult.PASS;
            breakBox(activeServer,box);
            return InteractionResult.SUCCESS;
        });
        UseEntityCallback.EVENT.register((player,level,hand,entity,hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            Box box = find(entity.entityTags());
            if (box == null || !"both".equals(box.interaction)) return InteractionResult.PASS;
            UUID playerId = player.getUUID();
            // A player may hold only one box at a time. Release any previous box.
            if (!playerId.equals(box.holder)) {
                for (Box other : boxes) if (playerId.equals(other.holder)) other.holder = null;
                box.holder=playerId;
            } else {
                // Right-click again to leave the box exactly where it was placed.
                box.holder=null;
            }
            return InteractionResult.SUCCESS;
        });
    }

    public synchronized void startDirectTikTok() {
        OwnerSettings.Config config = OwnerSettings.get();
        if (directTikTok != null) { directTikTok.close(); directTikTok = null; }
        currentTikTokUser = config.tiktokUsername();
        if (!config.enabled() || currentTikTokUser.isBlank()) return;
        directTikTok = new DirectTikTokService((user, text) -> {
            OwnerSettings.Config now = OwnerSettings.get();
            if (!now.enabled() || activeServer == null) return;
            if (incoming.size() >= 100) return;
            incoming.add(new ChatHttpServer.Chat(user,text,"",now.maxBoxes(),now.durationSeconds(),now.distance(),"both"));
        });
        directTikTok.connectAsync(currentTikTokUser);
    }

    public static LocLiveSingleplayerMod INSTANCE;

    private Box find(java.util.Set<String> tags) {
        for (String tag:tags) if (byTag.containsKey(tag)) return byTag.get(tag);
        return null;
    }
    private void spawn(MinecraftServer server,ChatHttpServer.Chat chat) {
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer player=server.getPlayerList().getPlayers().getFirst();
        if (player.level() != server.overworld()) return;
        while (boxes.size() >= chat.maxBoxes()) remove(server,boxes.peekFirst());
        String tag="loclivesp_"+Long.toHexString(System.nanoTime())+"_"+(++nextId);
        LiveChatMotion.Position initial=LiveChatMotion.aim(
            player.getX(), player.getY()+1.62, player.getZ(),
            player.getYRot(), player.getXRot(), chat.distance());
        double x=initial.x();
        double y=initial.y();
        double z=initial.z();
        String viewer=clean(chat.viewer(),28);
        String message=clean(chat.message(),80);
        // Singleplayer now accepts the SAME bounded rich card that Paper receives.
        // If Minecraft's map pipeline cannot be initialized, native text is retained.
        SingleplayerMapCards.MapResult picture = media.attach(server, player, chat.cardPng(), tag,
            x, y + .45, z, this::execute);
        if (!picture.ready()) {
            String nbt="{Tags:[\"loclivesp\",\""+tag+"\"],billboard:\"center\",alignment:\"center\","+
                "teleport_duration:3,"+
                (http == null ? LiveChatAppearance.DEFAULT : http.appearance()).displayFields(viewer,message)+"}";
            execute(server,"summon minecraft:text_display "+coord(x,y+.45,z)+" "+nbt);
        }
        execute(server,"summon minecraft:interaction "+coord(x,y,z)+" {Tags:[\"loclivesp\",\""+tag+"\"],"+
            hitboxNbt(http == null ? LiveChatAppearance.DEFAULT : http.appearance()).substring(1));
        execute(server,"playsound minecraft:entity.item.pickup master @a "+coord(x,y,z)+" 0.4 1.2");
        Box box=new Box(tag,"minecraft:overworld",System.currentTimeMillis()+chat.duration()*1000L,chat.interaction(),chat.distance(),initial,viewer,message);
        box.mediaCard = picture.ready();
        box.mediaMapId = picture.mapId();
        boxes.addLast(box);byTag.put(tag,box);
    }
    private void update(MinecraftServer server) {
        for (Box box:new ArrayList<>(boxes)) {
            if (System.currentTimeMillis() >= box.expires || server.getPlayerList().getPlayers().isEmpty()) {
                remove(server,box);
                continue;
            }
            if (box.holder == null) continue;
            ServerPlayer holder=server.getPlayerList().getPlayers().stream()
                .filter(p->p.getUUID().equals(box.holder)).findFirst().orElse(null);
            if (holder == null || holder.level() != server.overworld()) {
                box.holder=null;
                continue;
            }

            // Both yaw (left/right) and pitch (up/down) control the floating box.
            // Use the player's eye as the origin so vertical movements feel natural.
            LiveChatMotion.Position target=LiveChatMotion.aim(
                holder.getX(), holder.getY()+1.62, holder.getZ(),
                holder.getYRot(), holder.getXRot(), box.distance);
            LiveChatMotion.Position next=LiveChatMotion.ease(box.position,target);
            if (!LiveChatMotion.movedEnough(box.position,next)) continue;
            box.position=next;
            double x=next.x();
            double y=next.y();
            double z=next.z();
            // Keep the hitbox and visible text aligned; display NBT interpolates teleports.
            execute(server,"tp @e[type=minecraft:interaction,tag="+box.tag+"] "+coord(x,y,z));
            execute(server,"tp @e[type=minecraft:text_display,tag="+box.tag+"] "+coord(x,y+.45,z));
            execute(server,"tp @e[type=minecraft:item_display,tag="+box.tag+"] "+coord(x,y+.45,z));
        }
    }
    private void breakBox(MinecraftServer server,Box box) {
        if (server==null || box==null) return;
        execute(server,"execute at @e[type=minecraft:interaction,tag="+box.tag+",limit=1] run particle minecraft:end_rod ~ ~0.5 ~ 0.4 0.3 0.4 0.03 15 force");
        execute(server,"execute at @e[type=minecraft:interaction,tag="+box.tag+",limit=1] run playsound minecraft:block.glass.break master @a ~ ~ ~ 0.5 1.2");
        remove(server,box);
    }
    private void remove(MinecraftServer server,Box box) {
        if (box==null) return;
        if (server!=null) execute(server,"kill @e[tag="+box.tag+"]");
        if (box.mediaMapId >= 0) media.release(box.mediaMapId);
        boxes.remove(box);byTag.remove(box.tag);
    }
    private void purgeAbandonedChatEntities(MinecraftServer server) {
        if (server == null) return;
        // Tag is owned exclusively by LocLive Singleplayer. Do not kill untagged
        // vanilla text displays, lobby holograms, armor stands or other mods.
        execute(server,"kill @e[tag=loclivesp]");
    }
    private void execute(MinecraftServer server,String command) {
        // Built-in Minecraft commands, no Paper API or client-side dependencies.
        // Suppress success feedback from summon/tp/kill/particle/playsound in Singleplayer chat.
        // Do not disable gamerules globally: genuine player messages remain untouched.
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),command);
    }
    private static String hitboxNbt(LiveChatAppearance style) {
        double factor=style.scale()/100.0;
        return String.format(java.util.Locale.ROOT,"{width:%.2ff,height:%.2ff}",2.6*factor,1.3*factor);
    }
    private static String coord(double x,double y,double z) {
        return String.format(java.util.Locale.ROOT,"%.3f %.3f %.3f",x,y,z);
    }
    private static String clean(String input,int max) {
        // Preserve full Unicode emoji sequences. The previous whitelist stripped
        // pictographs, variation selectors and ZWJ, leaving blank boxes.
        String value=input.replaceAll("[\\p{Cc}\\p{Cs}\\p{Cf}&&[^\u200d\ufe0f\ufe0e]]", " ")
            .replaceAll("\\s+"," ").strip();
        if (value.codePointCount(0,value.length()) <= max) return value;
        int end=value.offsetByCodePoints(0,max);
        while (end < value.length()) {
            int cp=value.codePointAt(end);
            int previous=value.codePointBefore(end);
            if (cp==0x200d || cp==0xfe0f || cp==0xfe0e || previous==0x200d
                || Character.getType(cp)==Character.NON_SPACING_MARK || (cp>=0x1f3fb && cp<=0x1f3ff))
                end+=Character.charCount(cp);
            else break;
        }
        return value.substring(0,end);
    }
}
