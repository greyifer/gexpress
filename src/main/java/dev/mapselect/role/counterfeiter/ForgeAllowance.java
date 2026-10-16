package dev.mapselect.role.counterfeiter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Owned by one game world and accessed only on the server thread. */
final class ForgeAllowance {
    private final java.util.Map<UUID,Integer> choices=new java.util.HashMap<>();
    int selected(UUID player){return choices.getOrDefault(player,-1);}
    boolean forge(UUID player,int choice,BooleanSupplier deliver){boolean success=forge(player,deliver);if(success)choices.put(player,choice);return success;}
    private final Set<UUID> spent = new HashSet<>();
    boolean spent(UUID player) { return spent.contains(player); }
    boolean forge(UUID player, BooleanSupplier deliver) {
        if (spent(player) || !deliver.getAsBoolean()) return false;
        spent.add(player); return true;
    }
}
