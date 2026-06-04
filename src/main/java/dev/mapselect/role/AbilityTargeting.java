package dev.mapselect.role;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;
import java.util.function.Predicate;

public final class AbilityTargeting {
	private AbilityTargeting() {}

	public static <T extends PlayerEntity> T findLookTarget(PlayerEntity user, Iterable<T> candidates,
			double range, double padding, boolean requireSight, Predicate<T> predicate) {
		if (user == null || candidates == null || range <= 0.0D) return null;
		Vec3d start = user.getEyePos();
		Vec3d look = user.getRotationVec(1.0F).normalize();
		Vec3d end = start.add(look.multiply(range));
		T best = null;
		double bestDistance = range * range;
		for (T candidate : candidates) {
			if (candidate == null || candidate == user) continue;
			if (predicate != null && !predicate.test(candidate)) continue;
			Box box = candidate.getBoundingBox().expand(Math.max(0.0D, padding));
			Optional<Vec3d> hit = box.raycast(start, end);
			if (hit.isEmpty()) continue;
			if (requireSight && isBlocked(user, start, hit.get())) continue;
			double distance = start.squaredDistanceTo(hit.get());
			if (distance >= bestDistance) continue;
			best = candidate;
			bestDistance = distance;
		}
		return best;
	}

	private static boolean isBlocked(PlayerEntity user, Vec3d start, Vec3d target) {
		BlockHitResult hit = user.getWorld().raycast(new RaycastContext(
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
