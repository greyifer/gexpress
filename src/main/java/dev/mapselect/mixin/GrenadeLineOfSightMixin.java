package dev.mapselect.mixin;

import dev.doctor4t.wathe.entity.GrenadeEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.block.BlockState;
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
	private static final int MAX_PASS_THROUGH_HITS = 32;
	private static final double STEP_PAST_HIT = 0.08D;

	@Dynamic("Wathe's GrenadeEntity calls GameFunctions with named descriptors outside this project's mappings.")
	@Redirect(
		method = "method_7488(Lnet/minecraft/class_239;)V",
		at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/class_1657;ZLnet/minecraft/class_1657;Lnet/minecraft/class_2960;)V", remap = false),
		require = 0,
		remap = false
	)
	private void gexpress$grenadesNeedLineOfSight(PlayerEntity victim, boolean dead,
			PlayerEntity killer, Identifier reason) {
		Entity self = (Entity) (Object) this;
		if (!(self.getWorld() instanceof ServerWorld world)) return;
		if (victim instanceof ServerPlayerEntity serverVictim
				&& hasExplosionLineOfSight(world, self.getPos(), serverVictim)) {
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
		Vec3d direction = to.subtract(from);
		double totalDistanceSquared = direction.lengthSquared();
		if (totalDistanceSquared <= 0.0001D) return true;
		Vec3d step = direction.normalize().multiply(STEP_PAST_HIT);
		Vec3d start = from;
		for (int i = 0; i < MAX_PASS_THROUGH_HITS; i++) {
			BlockHitResult hit = world.raycast(new RaycastContext(start, to,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, victim));
			if (hit.getType() == HitResult.Type.MISS) return true;
			if (hit.getPos().squaredDistanceTo(from) + 0.05D >= totalDistanceSquared) return true;
			BlockState state = world.getBlockState(hit.getBlockPos());
			if (!GexpressConfig.isGrenadeLineOfSightPassThrough(state)) return false;
			Vec3d next = hit.getPos().add(step);
			if (next.squaredDistanceTo(from) <= start.squaredDistanceTo(from) + 0.0001D) return false;
			start = next;
		}
		return false;
	}
}
