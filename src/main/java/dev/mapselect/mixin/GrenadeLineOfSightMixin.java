package dev.mapselect.mixin;

import dev.doctor4t.wathe.entity.GrenadeEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GrenadeEntity.class, remap = false)
@SuppressWarnings("target")
public abstract class GrenadeLineOfSightMixin {
	@Dynamic("Wathe's GrenadeEntity calls GameFunctions with named descriptors outside this project's mappings.")
	@Redirect(
		method = "onCollision",
		at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Identifier;)V", remap = false),
		remap = false
	)
	private void gexpress$grenadesNeedLineOfSight(ServerPlayerEntity victim, boolean dead,
			PlayerEntity killer, Identifier reason) {
		Entity self = (Entity) (Object) this;
		if (!(self.getWorld() instanceof ServerWorld world)) return;
		if (hasExplosionLineOfSight(world, self.getPos(), victim)) {
			GameFunctions.killPlayer(victim, dead, killer, reason);
		}
	}

	private static boolean hasExplosionLineOfSight(ServerWorld world, Vec3d blastCenter, ServerPlayerEntity victim) {
		Vec3d center = blastCenter.add(0.0D, 0.35D, 0.0D);
		Vec3d eye = victim.getEyePos();
		Vec3d body = victim.getPos().add(0.0D, victim.getHeight() * 0.5D, 0.0D);
		return unobstructed(world, center, eye, victim) || unobstructed(world, center, body, victim);
	}

	private static boolean unobstructed(ServerWorld world, Vec3d from, Vec3d to, ServerPlayerEntity victim) {
		BlockHitResult hit = world.raycast(new RaycastContext(from, to,
			RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, victim));
		if (hit.getType() == HitResult.Type.MISS) return true;
		return hit.getPos().squaredDistanceTo(from) + 0.05D >= to.squaredDistanceTo(from);
	}
}
