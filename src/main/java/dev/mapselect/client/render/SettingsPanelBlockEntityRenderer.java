package dev.mapselect.client.render;

import dev.mapselect.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;

public final class SettingsPanelBlockEntityRenderer implements BlockEntityRenderer<SettingsPanelBlockEntity> {
    public SettingsPanelBlockEntityRenderer(BlockEntityRendererFactory.Context context) {}
    @Override public void render(SettingsPanelBlockEntity entity, float delta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        SettingsPanel panel = SettingsPanel.find(client.world, entity.getPos());
        if (panel == null || !panel.isOrigin(entity.getPos())) return;
        Vec3d facing = Vec3d.of(panel.facing().getVector());
        Vec3d center = Vec3d.ofCenter(panel.origin()).add(Vec3d.of(panel.right().getVector()).multiply((panel.width() - 1) * .5)).add(0, (panel.height() - 1) * .5, 0);
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        if (camera.squaredDistanceTo(center) > 64 * 64 || camera.subtract(center).dotProduct(facing) <= 0) return;
        int width = SettingsPanelLayout.width(panel.width()), height = SettingsPanelLayout.height(panel.height());
        Vec3d topLeft = Vec3d.ofCenter(panel.origin()).add(Vec3d.of(panel.right().getVector()).multiply(panel.width() - .5 - SettingsPanelLayout.MARGIN))
            .add(0, panel.height() - .5 - SettingsPanelLayout.MARGIN, 0).add(facing.multiply(.535)).subtract(Vec3d.of(entity.getPos()));
        matrices.push(); matrices.translate(topLeft.x, topLeft.y, topLeft.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - panel.facing().asRotation()));
        matrices.scale(-SettingsPanelLayout.SCALE, -SettingsPanelLayout.SCALE, SettingsPanelLayout.SCALE);
        int section=entity.section();
        int hover=hoverAction(client,panel,width,height,section);
        box(matrices,consumers,-3,-3,width+3,height+3,2.0f,0xFF142B3C);
        box(matrices,consumers,-1,-1,width+1,height+1,1.8f,0xFF385F72);
        box(matrices,consumers,0,0,width,height,1.6f,0xFF0B1720);
        centered(client,"LOBBY SETTINGS",width/2,3,0xEAF6FF,matrices,consumers);
        for(int tab=0;tab<4;tab++){
            int left=tab*width/4,right=(tab+1)*width/4;
            box(matrices,consumers,left+1,16,right-1,33,1.0f,hover==-10-tab?0xFF459D82:tab==section?0xFF26785F:0xFF253A4A);
            if(tab==section)box(matrices,consumers,left+1,31,right-1,33,.6f,0xFF83E5A9);
            String label=new String[]{"Game","Map","Roles","Modifiers"}[tab];
            centered(client,client.textRenderer.trimToWidth(label,right-left-4),(left+right)/2,20,hover==-10-tab||tab==section?0xFFFFFF:0xAFBEC8,matrices,consumers);
        }
        String[] categories=SettingsPanelCategories.labels(section);
        for(int category=0;category<categories.length;category++){
            int left=category*width/categories.length,right=(category+1)*width/categories.length;
            boolean selected=entity.category()==category;
            box(matrices,consumers,left+1,36,right-1,SettingsPanelLayout.HEADER-5,1.0f,hover==-100-category?0xFF638EA5:selected?0xFF3D627A:0xFF1C303F);
            centered(client,client.textRenderer.trimToWidth(categories[category],right-left-5),(left+right)/2,40,hover==-100-category||selected?0xFFFFFF:0x95AFBF,matrices,consumers);
        }
        boolean roleCards=section>=2;int fields=roleCards?3:1,cardWidth=SettingsPanelLayout.cardWidth(width);
        int visible=Math.min(entity.rows().size(),SettingsPanelLayout.rows(panel.width(),panel.height(),section));
        for(int i=0;i<visible;i+=fields){
            int card=i/fields,x=SettingsPanelLayout.cardX(card,width),y=SettingsPanelLayout.cardY(card,width,section);
            int cardHeight=SettingsPanelLayout.cardHeight(section)-4;
            box(matrices,consumers,x,y,x+cardWidth,y+cardHeight,1.2f,hover>=0&&hover/2>=i&&hover/2<i+fields?0xFF82D9F1:0xFF344A58);
            box(matrices,consumers,x+1,y+1,x+cardWidth-1,y+cardHeight-1,.9f,0xFF1B2C38);
            box(matrices,consumers,x+2,y+2,x+4,y+cardHeight-2,.6f,roleCards?0xFF6BD099:0xFF75B8E1);
            NbtCompound first=entity.rows().getCompound(i);
            String title=client.textRenderer.trimToWidth(first.getString("name"),cardWidth-14);
            draw(client,title,x+8,y+5,0xF0F7FA,matrices,consumers);
            for(int field=0;field<fields&&i+field<visible;field++){
                NbtCompound row=entity.rows().getCompound(i+field);int cy=y+SettingsPanelLayout.controlY(field,section);
                String value=pretty(row.getString("value"));
                if(roleCards){String label=new String[]{"Enabled","Chance","Count"}[field];draw(client,label,x+8,cy+2,0xAFBEC8,matrices,consumers);}
                int valueX=roleCards?x+cardWidth-40-client.textRenderer.getWidth(value):x+8;
                value=client.textRenderer.trimToWidth(value,roleCards?Math.max(8,cardWidth-92):Math.max(8,cardWidth-48));
                draw(client,value,Math.max(x+8,valueX),cy+2,value.equals("On")?0x83E5A9:value.equals("Off")?0xEA8989:0xFFFFFF,matrices,consumers);
                for(int button=0;button<2;button++){
                    int bx=x+cardWidth-36+button*16;
                    box(matrices,consumers,bx,cy,bx+14,cy+13,.3f,hover==(i+field)*2+button?0xFF79B3CC:0xFF547486);
                    centered(client,button==0?"-":"+",bx+7,cy+2,0xFFFFFF,matrices,consumers);
                }
            }
        }
        int fy=height-SettingsPanelLayout.FOOTER;
        box(matrices,consumers,0,fy,width, height,1.2f,0xFF142632);
        box(matrices,consumers,1,fy+2,27,height-2,.6f,hover==-3?0xFF638EA5:0xFF344F61);
        box(matrices,consumers,width-27,fy+2,width-1,height-2,.6f,hover==-4?0xFF638EA5:0xFF344F61);
        centered(client,"<",14,fy+5,0xFFFFFF,matrices,consumers);
        centered(client,">",width-14,fy+5,0xFFFFFF,matrices,consumers);
        int units=roleCards?3:1;
        String footer=entity.total()==0?"No settings":(entity.offset()/units+1)+" - "+((entity.offset()+entity.rows().size())/units)+" / "+(entity.total()/units);
        centered(client,client.textRenderer.trimToWidth(footer,width-62),width/2,fy+5,0xAFBEC8,matrices,consumers);
        matrices.pop();
    }
    private static int hoverAction(MinecraftClient client,SettingsPanel panel,int width,int height,int section){
        if(client.currentScreen!=null || !(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit) || hit.getType()!=net.minecraft.util.hit.HitResult.Type.BLOCK || hit.getSide()!=panel.facing())return Integer.MIN_VALUE;
        SettingsPanel pointed=SettingsPanel.find(client.world,hit.getBlockPos());
        if(pointed==null || !pointed.origin().equals(panel.origin()) || pointed.facing()!=panel.facing())return Integer.MIN_VALUE;
        double x=(panel.screenHorizontalCoordinate(hit.getPos())-SettingsPanelLayout.MARGIN)/SettingsPanelLayout.SCALE;
        double y=(panel.height()-panel.verticalCoordinate(hit.getPos())-SettingsPanelLayout.MARGIN)/SettingsPanelLayout.SCALE;
        return SettingsPanelLayout.action(x,y,width,height,section);
    }
    private static String pretty(String value){
        if(value.equals("true"))return "On";if(value.equals("false"))return "Off";
        if(value.contains("_"))return value.toLowerCase(java.util.Locale.ROOT).replace('_',' ');
        return value;
    }
    private static void centered(MinecraftClient client,String text,int x,int y,int color,MatrixStack matrices,VertexConsumerProvider consumers){draw(client,text,x-client.textRenderer.getWidth(text)/2,y,color,matrices,consumers);}
    // Each overlapping surface has a distinct depth; coplanar debug quads flicker under depth testing.
    private static void box(MatrixStack matrices,VertexConsumerProvider consumers,int left,int top,int right,int bottom,float depth,int color){
        var vertices=consumers.getBuffer(net.minecraft.client.render.RenderLayer.getDebugQuads());
        var matrix=matrices.peek().getPositionMatrix();
        vertices.vertex(matrix,left,top,depth).color(color);
        vertices.vertex(matrix,left,bottom,depth).color(color);
        vertices.vertex(matrix,right,bottom,depth).color(color);
        vertices.vertex(matrix,right,top,depth).color(color);
    }
    private static void draw(MinecraftClient client, String text, int x, int y, int color, MatrixStack matrices, VertexConsumerProvider consumers) {
        client.textRenderer.draw(text, x, y, 0xFF000000 | color, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
    }
    @Override public boolean rendersOutsideBoundingBox(SettingsPanelBlockEntity entity) { return true; }
    @Override public int getRenderDistance() { return 64; }
}
