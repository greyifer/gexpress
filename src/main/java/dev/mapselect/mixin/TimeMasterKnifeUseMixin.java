package dev.mapselect.mixin;

import dev.doctor4t.wathe.item.KnifeItem;
import dev.mapselect.role.timemaster.TimeMasterManager;
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
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(KnifeItem.class)
public abstract class TimeMasterKnifeUseMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockRewindKnifeReady(World world, PlayerEntity user, Hand hand,
			CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
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
		Vec3d end = start.add(user.getRotationVec(1.0F).normalize().multiply(3.0D));
		Entity best = null;
		Vec3d bestHit = null;
		double bestDistance = 9.0D;
		for (PlayerEntity target : user.getWorld().getPlayers()) {
			if (target == user || !target.isAlive() || target.isSpectator()) continue;
			Box box = target.getBoundingBox().expand(target.isSleeping() ? 0.55D : 0.2D);
			Optional<Vec3d> hit = box.raycast(start, end);
			if (hit.isEmpty()) continue;
			double distance = start.squaredDistanceTo(hit.get());
			if (distance >= bestDistance) continue;
			best = target;
			bestHit = hit.get();
			bestDistance = distance;
		}
		if (best != null) cir.setReturnValue(new EntityHitResult(best, bestHit));
	}
}
