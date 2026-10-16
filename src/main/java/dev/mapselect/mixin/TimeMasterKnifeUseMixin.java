package dev.mapselect.mixin;

import dev.doctor4t.wathe.item.KnifeItem;
import dev.mapselect.entity.TwinFakePlayerEntity;
import dev.mapselect.role.timemaster.TimeMasterManager;
import dev.mapselect.role.silent.SilentShadowComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

@Mixin(KnifeItem.class)
public abstract class TimeMasterKnifeUseMixin {
	private static final double GEXPRESS_KNIFE_RANGE = 2.35D;

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockRewindKnifeReady(World world, PlayerEntity user, Hand hand,
			CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		if (SilentShadowComponent.isShadowed(user)) {
			cir.setReturnValue(TypedActionResult.fail(user.getStackInHand(hand)));
			return;
		}
		if (!world.isClient && user instanceof ServerPlayerEntity player
				&& (TimeMasterManager.isFrozen(player) || TimeMasterManager.isRewinding(player))) {
			cir.setReturnValue(TypedActionResult.fail(user.getStackInHand(hand)));
		}
	}

	@Inject(method = "use", at = @At("RETURN"))
	private void gexpress$recordTimeMasterKnifeReady(World world, PlayerEntity user, Hand hand,
			CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		if (!world.isClient && user instanceof ServerPlayerEntity player) {
			TimeMasterManager.recordWeaponEvent(player, TimeMasterManager.WeaponEventType.KNIFE_READY);
		}
	}

	@Inject(method = "getKnifeTarget", at = @At("HEAD"), cancellable = true)
	private static void gexpress$allowSleepingKnifeTargets(PlayerEntity user, CallbackInfoReturnable<HitResult> cir) {
		if (user == null || user.getWorld() == null) return;
		Vec3d start = user.getEyePos();
		Vec3d end = start.add(user.getRotationVec(1.0F).normalize().multiply(GEXPRESS_KNIFE_RANGE));
		Entity best = null;
		Vec3d bestHit = null;
		double bestDistance = GEXPRESS_KNIFE_RANGE * GEXPRESS_KNIFE_RANGE;
		List<Entity> candidates = new ArrayList<>(user.getWorld().getPlayers());
		candidates.addAll(user.getWorld().getEntitiesByClass(TwinFakePlayerEntity.class,
			user.getBoundingBox().expand(GEXPRESS_KNIFE_RANGE + 1.0D),
			body -> body.isAlive() && !body.isRemoved()
				&& body.ownerId().filter(ownerId -> !ownerId.equals(user.getUuid())).isPresent()));
		for (Entity target : candidates) {
			if (target == user || !target.isAlive()
					|| target instanceof PlayerEntity player && player.isSpectator()) continue;
			Box box = target.getBoundingBox().expand(
				target instanceof PlayerEntity player && player.isSleeping() ? 0.55D : 0.2D);
			Optional<Vec3d> hit = box.raycast(start, end);
			if (hit.isEmpty()) continue;
			if (gexpress$isBlocked(user, start, hit.get())) continue;
			double distance = start.squaredDistanceTo(hit.get());
			if (distance >= bestDistance) continue;
			best = target;
			bestHit = hit.get();
			bestDistance = distance;
		}
		if (best != null) cir.setReturnValue(new EntityHitResult(best, bestHit));
	}

	private static boolean gexpress$isBlocked(PlayerEntity user, Vec3d start, Vec3d target) {
		HitResult hit = user.getWorld().raycast(new RaycastContext(
			start,
			target,
			RaycastContext.ShapeType.COLLIDER,
			RaycastContext.FluidHandling.NONE,
			user
		));
		return hit != null && hit.getType() == HitResult.Type.BLOCK
			&& start.squaredDistanceTo(hit.getPos()) + 0.01D < start.squaredDistanceTo(target);
	}
}
