package dev.mapselect.block;
/** Shared card geometry used by rendering and authoritative hit testing. */
public final class SettingsPanelLayout {
    public static final float SCALE=.0125F,MARGIN=.075F;
    public static final int HEADER=56,FOOTER=18,ROW=36,GAP=5;
    private SettingsPanelLayout(){}
    public static int width(int blocks){return (int)Math.floor((blocks-2*MARGIN)/SCALE);}
    public static int height(int blocks){return width(blocks);}
    public static int columns(int width){return width>=220?2:1;}
    public static int cardHeight(int section){return section>=2?72:ROW;}
    public static int rows(int blocks){return rows(1,blocks,0);}
    public static int rows(int widthBlocks,int heightBlocks,int section){return Math.max(1,(height(heightBlocks)-HEADER-FOOTER)/cardHeight(section))*columns(width(widthBlocks))*(section>=2?3:1);}
    public static int cardWidth(int width){return (width-GAP*(columns(width)-1))/columns(width);}
    public static int cardX(int card,int width){return card%columns(width)*(cardWidth(width)+GAP);}
    public static int cardY(int card,int width,int section){return HEADER+card/columns(width)*cardHeight(section);}
    public static int controlY(int field,int section){return section>=2?20+field*16:18;}
    public static int action(double x,double y,int width,int height){return action(x,y,width,height,0);}
    public static int action(double x,double y,int width,int height,int section){
        if(x<0||y<0||x>=width||y>=height)return Integer.MIN_VALUE;
        if(y>=16&&y<34)return -10-Math.min(3,(int)(x*4/width));
        if(y>=36&&y<HEADER-4)return -100-Math.min(SettingsPanelCategories.count(section)-1,(int)(x*SettingsPanelCategories.count(section)/width));
        if(y<HEADER)return Integer.MIN_VALUE;
        if(y>=height-FOOTER)return x<28?-3:x>=width-28?-4:Integer.MIN_VALUE;
        int column=(int)x/(cardWidth(width)+GAP),localX=(int)x-column*(cardWidth(width)+GAP);
        if(column>=columns(width)||localX>=cardWidth(width))return Integer.MIN_VALUE;
        int cardRow=((int)y-HEADER)/cardHeight(section),localY=((int)y-HEADER)%cardHeight(section);
        if(cardRow>=(height-HEADER-FOOTER)/cardHeight(section))return Integer.MIN_VALUE;
        int field=section>=2?(localY-20)/16:0;
        if(localY<controlY(0,section)||field<0||field>=(section>=2?3:1)||localY>=controlY(field,section)+13)return Integer.MIN_VALUE;
        if(localX<cardWidth(width)-36||localX>=cardWidth(width)-4)return Integer.MIN_VALUE;
        int row=(cardRow*columns(width)+column)*(section>=2?3:1)+field;
        return row*2+(localX>=cardWidth(width)-20?1:0);
    }
}
