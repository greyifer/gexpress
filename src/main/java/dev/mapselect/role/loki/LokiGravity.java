package dev.mapselect.role.loki;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import java.util.*;

/** Common bridge avoids loading client classes on a dedicated server. */
public final class LokiGravity {
    private static final Map<UUID,Long> CLIENT = new HashMap<>();
    private LokiGravity() {}
    public static void clearClient(){CLIENT.clear();}
    public static void client(UUID player,long until){CLIENT.put(player,until);}
    public static boolean flipped(Entity entity){
        if (!(entity instanceof PlayerEntity))return false;
        return entity.getWorld().isClient ? CLIENT.getOrDefault(entity.getUuid(),0L)>entity.getWorld().getTime()
            : LokiManager.gravityFlipped(entity);
    }
}
