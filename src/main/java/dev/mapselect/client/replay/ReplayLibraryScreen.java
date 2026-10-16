package dev.mapselect.client.replay;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

public final class ReplayLibraryScreen extends Screen {
    public String error="";
    private int page;
    public ReplayLibraryScreen() { super(Text.literal("Past matches")); }
    @Override public boolean shouldPause() { return false; }
    @Override protected void init() {
        if (!ClientReplay.canAccess()) { client.setScreen(null); return; }
        int rows=Math.max(1,(height-110)/30),start=page*rows;
        for(int i=start;i<Math.min(ClientReplay.catalog.size(),start+rows);i++) {
            NbtCompound replay=ClientReplay.catalog.getCompound(i); String date=replay.getString("date");
            String label=date.replace('T',' ').replace("Z"," UTC")+"  ·  "+ReplayControlsScreen.time(replay.getInt("duration"))+"  ·  "+replay.getList("players",10).size()+" players";
            addDrawableChild(ButtonWidget.builder(Text.literal(label),b->{ClientReplay.loading=true; client.setScreen(new ReplayLoadingScreen()); ClientReplay.control("open",replay.getString("id"));}).dimensions(width/2-220,48+(i-start)*30,440,24).build());
        }
        if(page>0) addDrawableChild(ButtonWidget.builder(Text.literal("Previous"),b->{page--;clearAndInit();}).dimensions(width/2-120,height-42,80,20).build());
        if(start+rows<ClientReplay.catalog.size()) addDrawableChild(ButtonWidget.builder(Text.literal("Next"),b->{page++;clearAndInit();}).dimensions(width/2+40,height-42,80,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"),b->close()).dimensions(width/2-36,height-42,72,20).build());
    }
    @Override public void close() { ClientReplay.control("stop",""); super.close(); }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        renderBackground(context,mouseX,mouseY,delta); super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,20,0xFFFFFFFF);
        if(ClientReplay.catalog.isEmpty()) context.drawCenteredTextWithShadow(textRenderer,Text.literal("No completed matches have been recorded yet."),width/2,60,0xFFCAD7E8);
        if(!error.isEmpty()) context.drawCenteredTextWithShadow(textRenderer,Text.literal(error),width/2,height-64,0xFFFFB8B8);
    }
}
