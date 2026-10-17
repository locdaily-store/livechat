package id.loclive.singleplayer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Owner-exclusive native Minecraft screen, reachable through Mod Menu. */
public final class OwnerSettingsScreen extends Screen {
    private final Screen previous;
    private EditBox username, amount;
    private boolean enabled;
    private String error="";
    public OwnerSettingsScreen(Screen previous){super(Component.literal("LocLive • RinxAsoy"));this.previous=previous;}
    @Override protected void init(){
        OwnerSettings.Config c=OwnerSettings.get();enabled=c.enabled();
        int x=width/2-112,y=Math.max(35,height/2-80);
        username=new EditBox(font,x,y,224,20,Component.literal("Username TikTok"));
        username.setMaxLength(31);username.setValue(c.tiktokUsername());addRenderableWidget(username);
        amount=new EditBox(font,x,y+34,224,20,Component.literal("Jumlah box"));
        amount.setMaxLength(2);amount.setValue(Integer.toString(c.maxBoxes()));addRenderableWidget(amount);
        addRenderableWidget(Button.builder(Component.literal(enabled?"TikTok: Aktif":"TikTok: Nonaktif"),b->{
            enabled=!enabled;b.setMessage(Component.literal(enabled?"TikTok: Aktif":"TikTok: Nonaktif"));
        }).bounds(x,y+66,224,20).build());
        addRenderableWidget(Button.builder(Component.literal("Simpan & Hubungkan"),b->save())
            .bounds(x,y+94,110,20).build());
        addRenderableWidget(Button.builder(Component.literal("Kembali"),b->minecraft.setScreen(previous))
            .bounds(x+114,y+94,110,20).build());
    }
    private void save(){
        try{
            OwnerSettings.Config old=OwnerSettings.get();
            OwnerSettings.save(new OwnerSettings.Config(username.getValue(),enabled,
                Integer.parseInt(amount.getValue()),old.durationSeconds(),old.distance(),old.opacity(),old.scale()));
            if(LocLiveSingleplayerMod.INSTANCE!=null)LocLiveSingleplayerMod.INSTANCE.startDirectTikTok();
            minecraft.setScreen(previous);
        }catch(Exception ex){error="Pengaturan belum tersimpan: "+ex.getMessage();}
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        super.render(g,mouseX,mouseY,partialTick);
        int y=Math.max(35,height/2-80);
        g.drawCenteredString(font,"LOCLIVE  •  BY RINXASOY",width/2,y-25,0x55DDDD);
        g.drawString(font,"TikTok @username",width/2-112,y-10,0xFFFFFF);
        g.drawString(font,"Maks. box Live Chat",width/2-112,y+24,0xFFFFFF);
        g.drawCenteredString(font,error.isEmpty()?"Singleplayer • Tanpa CMD/BAT":error,
            width/2,y+130,error.isEmpty()?0xAABBBB:0xFF7777);
    }
}
