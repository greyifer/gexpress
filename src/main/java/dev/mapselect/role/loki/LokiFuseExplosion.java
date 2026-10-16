package dev.mapselect.role.loki;
import dev.doctor4t.wathe.entity.GrenadeEntity;
import dev.doctor4t.wathe.index.WatheEntities;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.BlockHitResult;
/** Invoke the real grenade collision, including other mods' explosion hooks. */
final class LokiFuseExplosion extends GrenadeEntity {
    private LokiFuseExplosion(ServerWorld world){super(WatheEntities.GRENADE,world);}
    static void explode(ServerWorld world,Vec3d position,PlayerEntity caster){
        var grenade=new LokiFuseExplosion(world);grenade.setPosition(position);grenade.setOwner(caster);
        grenade.onCollision(new BlockHitResult(position,Direction.UP,BlockPos.ofFloored(position),false));
    }
}
