package dev.mapselect.block;
import java.util.Locale;
/** Shared category names and deterministic placement of optional-mod settings. */
public final class SettingsPanelCategories {
    private static final String[][] LABELS={{"General","Cooldowns","Prices","Rules","Other"},{"All","World","Train"},{"All","Civilian","Killer","Neutral","Mafia"},{"All","Gexpress","Other mods"}};
    private SettingsPanelCategories(){}
    public static String[] labels(int section){return LABELS[Math.floorMod(section,LABELS.length)].clone();}
    public static int count(int section){return labels(section).length;}
    public static boolean roleCards(int section){return section==2||section==3;}
    public static int gameCategory(String key){
        String s=key.toLowerCase(Locale.ROOT);
        if(s.contains("price")||s.contains("cost")||s.contains("coins")||s.contains("fare"))return 2;
        if(s.contains("enable")||s.contains("modify")||s.contains("instinct")||s.contains("fakerole")||s.contains("limit"))return 3;
        if(s.contains("cooldown")||s.contains("duration")||s.contains("time"))return 1;
        if(s.startsWith("hml."))return 0;
        return 4;
    }
}
