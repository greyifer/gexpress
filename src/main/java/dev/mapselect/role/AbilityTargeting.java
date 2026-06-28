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
	private static final double MIN_TARGET_PADDING = 0.15D;

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
			Box box = candidate.getBoundingBox().expand(targetPadding(padding));
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

	public static <T extends PlayerEntity> boolean canTarget(PlayerEntity user, T candidate, double range,
			double padding, boolean requireSight, Predicate<T> predicate) {
		if (user == null || candidate == null || candidate == user || range <= 0.0D) return false;
		if (predicate != null && !predicate.test(candidate)) return false;
		Vec3d start = user.getEyePos();
		Box box = candidate.getBoundingBox().expand(targetPadding(padding));
		Vec3d closest = closestPoint(start, box);
		if (start.squaredDistanceTo(closest) > range * range) return false;
		if (!requireSight) return true;
		if (!isBlocked(user, start, closest)) return true;
		Vec3d eye = candidate.getEyePos();
		return start.squaredDistanceTo(eye) <= range * range && !isBlocked(user, start, eye);
	}

	private static Vec3d closestPoint(Vec3d point, Box box) {
		return new Vec3d(
			clamp(point.x, box.minX, box.maxX),
			clamp(point.y, box.minY, box.maxY),
			clamp(point.z, box.minZ, box.maxZ)
		);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double targetPadding(double padding) {
		return Math.max(MIN_TARGET_PADDING, padding);
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
