package dev.mapselect.role;

import dev.mapselect.entity.TwinFakePlayerEntity;
import dev.mapselect.role.twins.TwinsManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;
import java.util.List;
import java.util.Locale;
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
			for (LivingEntity physicalTarget : physicalTargets(candidate)) {
				Box box = physicalTarget.getBoundingBox().expand(targetPadding(padding));
				Optional<Vec3d> hit = box.raycast(start, end);
				if (hit.isEmpty()) continue;
				if (requireSight && isBlocked(user, start, hit.get())) continue;
				double distance = start.squaredDistanceTo(hit.get());
				if (distance >= bestDistance) continue;
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	public static <T extends PlayerEntity> boolean canTarget(PlayerEntity user, T candidate, double range,
			double padding, boolean requireSight, Predicate<T> predicate) {
		if (user == null || candidate == null || candidate == user || range <= 0.0D) return false;
		if (predicate != null && !predicate.test(candidate)) return false;
		Vec3d start = user.getEyePos();
		for (LivingEntity physicalTarget : physicalTargets(candidate)) {
			Box box = physicalTarget.getBoundingBox().expand(targetPadding(padding));
			Vec3d closest = closestPoint(start, box);
			if (start.squaredDistanceTo(closest) > range * range) continue;
			if (!requireSight || !isBlocked(user, start, closest)) return true;
			Vec3d eye = physicalTarget.getEyePos();
			if (start.squaredDistanceTo(eye) <= range * range && !isBlocked(user, start, eye)) return true;
		}
		return false;
	}

	public static <T extends PlayerEntity> String lookTargetFailure(PlayerEntity user, Iterable<T> candidates,
			double range, double padding, boolean requireSight, Predicate<T> predicate, String actionVerb) {
		String action = actionVerb == null || actionVerb.isBlank() ? "use this ability" : actionVerb;
		if (user == null || candidates == null || range <= 0.0D) return "Cannot find a target right now.";
		Vec3d start = user.getEyePos();
		boolean hasValidCandidate = false;
		boolean hasCandidateInRange = false;
		boolean hasVisibleCandidate = false;
		for (T candidate : candidates) {
			if (candidate == null || candidate == user) continue;
			if (predicate != null && !predicate.test(candidate)) continue;
			hasValidCandidate = true;
			for (LivingEntity physicalTarget : physicalTargets(candidate)) {
				Box box = physicalTarget.getBoundingBox().expand(targetPadding(padding));
				Vec3d closest = closestPoint(start, box);
				if (start.squaredDistanceTo(closest) > range * range) continue;
				hasCandidateInRange = true;
				if (!requireSight || !isBlocked(user, start, closest)
						|| (start.squaredDistanceTo(physicalTarget.getEyePos()) <= range * range
							&& !isBlocked(user, start, physicalTarget.getEyePos()))) {
					hasVisibleCandidate = true;
					break;
				}
			}
			if (hasVisibleCandidate) break;
		}
		if (!hasValidCandidate) return "No valid player to " + action + ".";
		if (!hasCandidateInRange) return "Move within " + formatRange(range) + " blocks to " + action + ".";
		if (!hasVisibleCandidate) return "Line of sight is blocked.";
		return "Aim at a valid player to " + action + ".";
	}

	private static Vec3d closestPoint(Vec3d point, Box box) {
		return new Vec3d(
			clamp(point.x, box.minX, box.maxX),
			clamp(point.y, box.minY, box.maxY),
			clamp(point.z, box.minZ, box.maxZ)
		);
	}

	private static List<LivingEntity> physicalTargets(PlayerEntity candidate) {
		if (candidate instanceof ServerPlayerEntity serverPlayer) {
			TwinFakePlayerEntity body = TwinsManager.bodyFor(serverPlayer);
			if (body != null && body.isAlive() && !body.isRemoved()) return List.of(candidate, body);
		}
		return List.of(candidate);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double targetPadding(double padding) {
		return Math.max(MIN_TARGET_PADDING, padding);
	}

	private static String formatRange(double range) {
		double rounded = Math.rint(range);
		if (Math.abs(range - rounded) < 1.0E-6D) return Integer.toString((int) rounded);
		return String.format(Locale.ROOT, "%.1f", range);
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
