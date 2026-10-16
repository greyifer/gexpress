package dev.mapselect.role.loki;
import net.minecraft.entity.Entity;
import java.util.UUID;
import java.util.function.Function;
/** UUID resolution for display only; does not copy permissions or change actor identity. */
public final class BodyDisplayIdentity {
    private static Function<Entity,UUID> clientOwner=e->null;
    private BodyDisplayIdentity(){}
    public static void clientResolver(Function<Entity,UUID> resolver){clientOwner=resolver;}
    public static UUID uuid(Entity entity){
        if(entity instanceof dev.mapselect.entity.TwinFakePlayerEntity body)return body.ownerId().orElse(entity.getUuid());
        UUID owner=entity.getWorld().isClient?clientOwner.apply(entity):null;
        return owner==null?entity.getUuid():owner;
    }
}
