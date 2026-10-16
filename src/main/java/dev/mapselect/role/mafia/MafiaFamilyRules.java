package dev.mapselect.role.mafia;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

public final class MafiaFamilyRules {
    private MafiaFamilyRules() {}
    /** Victory requires one known family root for every survivor. */
    public static <T> UUID soleRoot(Collection<T> survivors,Function<T,UUID> rootFor) {
        UUID winner=null;
        for(T survivor:survivors) {
            UUID root=rootFor.apply(survivor);
            if(root==null || (winner!=null && !winner.equals(root))) return null;
            winner=root;
        }
        return winner;
    }
}
