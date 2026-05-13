package dev.mapselect.role;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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
			if (requireSight && !user.canSee(candidate)) continue;
			Box box = candidate.getBoundingBox().expand(Math.max(0.0D, padding));
			Optional<Vec3d> hit = box.raycast(start, end);
			if (hit.isEmpty()) continue;
			double distance = start.squaredDistanceTo(hit.get());
			if (distance >= bestDistance) continue;
			best = candidate;
			bestDistance = distance;
		}
		return best;
	}
}
