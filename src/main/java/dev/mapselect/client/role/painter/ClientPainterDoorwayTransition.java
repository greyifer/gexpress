package dev.mapselect.client.role.painter;

import dev.mapselect.mixin.client.CameraAccessor;
import dev.mapselect.network.role.painter.PainterDoorwayCrossPayload;
import dev.mapselect.network.role.painter.PainterDoorwayTransitionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ClientPainterDoorwayTransition {
	private static final double DESTINATION_VIEW_OFFSET = 8.0D / 16.0D;
	private static final double DOORWAY_EXIT_CLEARANCE = DESTINATION_VIEW_OFFSET;
	private static final double PORTAL_HALF_WIDTH = 0.62D;
	private static final double PORTAL_MIN_Y_INSET = 0.05D;
	private static final double PORTAL_MAX_Y_INSET = 1.95D;
	private static final int CLIENT_CROSSING_COOLDOWN_TICKS = 12;
	private static Vec3d offset = Vec3d.ZERO;
	private static Object world;
	private static Vec3d lastEyePos;
	private static int ageTicks;
	private static int durationTicks;
	private static final Map<BlockPos, Integer> recentCrossings = new HashMap<>();

	private ClientPainterDoorwayTransition() {}

	public static void start(MinecraftClient client, PainterDoorwayTransitionPayload payload) {
		if (client == null || client.world == null || payload == null) return;
		Vec3d nextOffset = payload.offset();
		if (nextOffset.lengthSquared() < 0.0009D) {
			clearCorrection();
			return;
		}
		world = client.world;
		offset = nextOffset;
		ageTicks = 0;
		durationTicks = payload.durationTicks();
	}

	public static void tick(MinecraftClient client) {
		if (client == null || client.world != world) {
			clearCrossingState(client == null ? null : client.world);
		}
		tickRecentCrossings();
		if (durationTicks <= 0) return;
		if (client == null || client.world != world || ++ageTicks >= durationTicks) {
			clearCorrection();
		}
	}

	public static void checkClientCrossing(Camera camera, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || client.player == null || camera == null) {
			lastEyePos = null;
			return;
		}
		if (world != client.world) {
			clearCrossingState(client.world);
		}
		Vec3d eyePos = camera.getPos();
		if (isImmersivePortalsAvailable()) {
			lastEyePos = eyePos;
			return;
		}
		if (lastEyePos == null || lastEyePos.squaredDistanceTo(eyePos) > 36.0D) {
			lastEyePos = eyePos;
			return;
		}
		for (ClientPainterState.DoorwayTint doorway : ClientPainterState.doorways()) {
			if (doorway == null || doorway.lowerPos() == null || doorway.through() == null) continue;
			if (recentCrossings.containsKey(doorway.lowerPos())) continue;
			Vec3d center = sourcePortalPlaneCenter(doorway.lowerPos(), doorway.facing(), doorway.through());
			double previousSide = doorwaySide(lastEyePos, center, doorway.through());
			double currentSide = doorwaySide(eyePos, center, doorway.through());
			if (previousSide > 0.02D || currentSide <= 0.0D) continue;
			Vec3d collision = collisionPoint(lastEyePos, eyePos, previousSide, currentSide);
			if (!withinPortalFace(collision, doorway.lowerPos(), doorway.through())) continue;
			predictCrossing(client, camera, doorway, eyePos);
			return;
		}
		lastEyePos = eyePos;
	}

	public static void apply(Camera camera, float tickDelta) {
		if (camera == null || durationTicks <= 0) return;
		float progress = MathHelper.clamp((ageTicks + tickDelta) / (float) durationTicks, 0.0F, 1.0F);
		float weight = 1.0F - progress;
		weight *= weight;
		if (weight <= 0.001F) return;
		((CameraAccessor) camera).gexpress$setPos(camera.getPos().add(offset.multiply(weight)));
	}

	private static void predictCrossing(MinecraftClient client, Camera camera,
			ClientPainterState.DoorwayTint doorway, Vec3d eyePos) {
		ClientPlayerEntity player = client.player;
		if (player == null || doorway == null || !ClientPlayNetworking.canSend(PainterDoorwayCrossPayload.ID)) return;
		Vec3d sourceFeet = player.getPos();
		float oldYaw = player.getYaw();
		float oldPitch = player.getPitch();
		ClientPlayNetworking.send(new PainterDoorwayCrossPayload(doorway.lowerPos(), sourceFeet.x, sourceFeet.y,
			sourceFeet.z, oldYaw, oldPitch));

		float yawDelta = MathHelper.wrapDegrees(
			doorway.destinationThrough().asRotation() - doorway.through().asRotation());
		float newYaw = oldYaw + yawDelta;
		Vec3d eyeOffset = eyePos.subtract(sourceFeet);
		Vec3d destinationEye = pushToDestinationSide(doorway,
			transformedDoorwayPosition(doorway, eyePos, 0.0D), DOORWAY_EXIT_CLEARANCE);
		Vec3d destinationFeet = destinationEye.subtract(eyeOffset);
		Vec3d safeDestinationFeet = nearestSafeDoorwayDestination(client, doorway, destinationFeet);
		Vec3d safeDelta = safeDestinationFeet.subtract(destinationFeet);
		Vec3d previousEye = new Vec3d(player.prevX, player.prevY, player.prevZ).add(eyeOffset);
		Vec3d previousFeet = pushToDestinationSide(doorway,
			transformedDoorwayPosition(doorway, previousEye, 0.0D), DOORWAY_EXIT_CLEARANCE)
			.subtract(eyeOffset);
		destinationEye = destinationEye.add(safeDelta);
		previousFeet = previousFeet.add(safeDelta);

		player.updatePositionAndAngles(safeDestinationFeet.x, safeDestinationFeet.y, safeDestinationFeet.z,
			newYaw, oldPitch);
		player.prevX = previousFeet.x;
		player.prevY = previousFeet.y;
		player.prevZ = previousFeet.z;
		player.lastRenderX = previousFeet.x;
		player.lastRenderY = previousFeet.y;
		player.lastRenderZ = previousFeet.z;
		player.setVelocity(rotateHorizontal(player.getVelocity(), Math.toRadians(yawDelta)));
		((CameraAccessor) camera).gexpress$setPos(destinationEye);
		lastEyePos = destinationEye;
		recentCrossings.put(doorway.lowerPos(), CLIENT_CROSSING_COOLDOWN_TICKS);
	}

	private static Vec3d transformedDoorwayPosition(ClientPainterState.DoorwayTint doorway, Vec3d pos,
			double extraThroughOffset) {
		Vec3d sourceCenter = sourcePortalPlaneCenter(doorway.lowerPos(), doorway.facing(), doorway.through());
		Vec3d destinationCenter = portalPlaneCenter(doorway.destinationLowerPos(),
			doorway.destinationFacing(), doorway.destinationThrough());
		Vec3d relative = pos.subtract(sourceCenter);
		Vec3d sourceForward = horizontalVector(doorway.through());
		Vec3d sourceRight = horizontalVector(doorway.through().rotateYClockwise());
		Vec3d destinationForward = horizontalVector(doorway.destinationThrough());
		Vec3d destinationRight = horizontalVector(doorway.destinationThrough().rotateYClockwise());
		double forward = relative.dotProduct(sourceForward) + extraThroughOffset;
		double right = relative.dotProduct(sourceRight);
		return destinationCenter
			.add(destinationForward.multiply(forward))
			.add(destinationRight.multiply(right))
			.add(0.0D, relative.y, 0.0D);
	}

	private static Vec3d pushToDestinationSide(ClientPainterState.DoorwayTint doorway, Vec3d pos,
			double minimumDistance) {
		if (doorway == null || pos == null || doorway.destinationThrough() == null) return pos;
		Vec3d center = portalPlaneCenter(doorway.destinationLowerPos(),
			doorway.destinationFacing(), doorway.destinationThrough());
		Vec3d normal = Vec3d.of(doorway.destinationThrough().getVector());
		double distance = pos.subtract(center).dotProduct(normal);
		return distance >= minimumDistance
			? pos
			: pos.add(normal.multiply(minimumDistance - distance));
	}

	private static Vec3d nearestSafeDoorwayDestination(MinecraftClient client,
			ClientPainterState.DoorwayTint doorway, Vec3d transformed) {
		if (client == null || client.world == null || client.player == null || transformed == null) {
			return transformed == null ? Vec3d.ZERO : transformed;
		}
		Vec3d fallback = doorway.destination();
		if (isSafeStandPos(client, transformed.x, transformed.y, transformed.z)) return transformed;
		if (fallback != null && isSafeStandPos(client, fallback.x, fallback.y, fallback.z)) return fallback;
		Vec3d best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Vec3d seed : new Vec3d[] {transformed, fallback}) {
			if (seed == null) continue;
			Vec3d candidate = scanSafeDoorwayDestination(client, doorway, seed, transformed);
			if (candidate == null) continue;
			double distance = candidate.squaredDistanceTo(transformed);
			if (distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best == null ? (fallback == null ? transformed : fallback) : best;
	}

	private static Vec3d scanSafeDoorwayDestination(MinecraftClient client,
			ClientPainterState.DoorwayTint doorway, Vec3d seed, Vec3d transformed) {
		BlockPos base = BlockPos.ofFloored(seed);
		Direction through = doorway.destinationThrough() == null || doorway.destinationThrough().getAxis().isVertical()
			? Direction.NORTH
			: doorway.destinationThrough();
		Direction right = through.getAxis().isVertical()
			? Direction.EAST
			: through.rotateYClockwise();
		int[] yOffsets = {0, 1, -1, 2, -2};
		int[] sideOffsets = {0, -1, 1, -2, 2};
		Vec3d best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int yOffset : yOffsets) {
			for (int forward = 0; forward <= 4; forward++) {
				for (int side : sideOffsets) {
					BlockPos feet = base.offset(through, forward).offset(right, side).add(0, yOffset, 0);
					Vec3d candidate = Vec3d.ofBottomCenter(feet);
					if (!isSafeStandPos(client, candidate.x, candidate.y, candidate.z)) continue;
					double distance = candidate.squaredDistanceTo(transformed);
					if (distance < bestDistance) {
						best = candidate;
						bestDistance = distance;
					}
				}
			}
		}
		return best;
	}

	private static boolean isSafeStandPos(MinecraftClient client, double x, double y, double z) {
		ClientPlayerEntity player = client == null ? null : client.player;
		if (player == null || client.world == null) return false;
		Box current = player.getBoundingBox();
		Box target = current.offset(x - player.getX(), y - player.getY(), z - player.getZ());
		return client.world.isSpaceEmpty(player, target.contract(1.0E-7D)) && hasValidSupport(client, target);
	}

	private static boolean hasValidSupport(MinecraftClient client, Box target) {
		double supportY = target.minY - 0.08D;
		double minX = target.minX + 0.05D;
		double maxX = target.maxX - 0.05D;
		double minZ = target.minZ + 0.05D;
		double maxZ = target.maxZ - 0.05D;
		double centerX = (target.minX + target.maxX) * 0.5D;
		double centerZ = (target.minZ + target.maxZ) * 0.5D;
		double[][] samples = {
			{ centerX, centerZ },
			{ minX, minZ },
			{ minX, maxZ },
			{ maxX, minZ },
			{ maxX, maxZ }
		};
		for (double[] sample : samples) {
			BlockPos supportPos = BlockPos.ofFloored(sample[0], supportY, sample[1]);
			if (!client.world.getBlockState(supportPos).getCollisionShape(client.world, supportPos).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static Vec3d portalPlaneCenter(BlockPos lower, Direction facing, Direction through) {
		Vec3d center = Vec3d.ofCenter(lower).add(0.0D, 0.50D, 0.0D);
		if (through == null || through.getAxis().isVertical()) return center;
		return center.add(Vec3d.of(through.getVector()).multiply(DESTINATION_VIEW_OFFSET));
	}

	private static Vec3d sourcePortalPlaneCenter(BlockPos lower, Direction facing, Direction through) {
		return Vec3d.ofCenter(lower).add(0.0D, 0.50D, 0.0D);
	}

	private static double doorwaySide(Vec3d pos, Vec3d center, Direction through) {
		Vec3d normal = new Vec3d(through.getOffsetX(), 0.0D, through.getOffsetZ());
		return pos.subtract(center).dotProduct(normal);
	}

	private static Vec3d collisionPoint(Vec3d previous, Vec3d current, double previousSide, double currentSide) {
		double denominator = previousSide - currentSide;
		double t = denominator == 0.0D ? 1.0D : MathHelper.clamp(previousSide / denominator, 0.0D, 1.0D);
		return previous.lerp(current, t);
	}

	private static boolean withinPortalFace(Vec3d pos, BlockPos lower, Direction through) {
		if (pos == null || lower == null || through == null || through.getAxis().isVertical()) return false;
		double localY = pos.y - lower.getY();
		if (localY < PORTAL_MIN_Y_INSET || localY > PORTAL_MAX_Y_INSET) return false;
		Direction rightDirection = through.rotateYClockwise();
		Vec3d right = new Vec3d(rightDirection.getOffsetX(), 0.0D, rightDirection.getOffsetZ());
		double horizontal = pos.subtract(Vec3d.ofCenter(lower)).dotProduct(right);
		return Math.abs(horizontal) <= PORTAL_HALF_WIDTH;
	}

	private static Vec3d rotateHorizontal(Vec3d value, double yawRadians) {
		if (value == null) return Vec3d.ZERO;
		double cos = Math.cos(yawRadians);
		double sin = Math.sin(yawRadians);
		return new Vec3d(value.x * cos - value.z * sin, value.y, value.x * sin + value.z * cos);
	}

	private static Vec3d horizontalVector(Direction direction) {
		if (direction == null || direction.getAxis().isVertical()) return Vec3d.ZERO;
		return new Vec3d(direction.getOffsetX(), 0.0D, direction.getOffsetZ()).normalize();
	}

	private static boolean isImmersivePortalsAvailable() {
		if (FabricLoader.getInstance().isModLoaded("immersive_portals")
				|| FabricLoader.getInstance().isModLoaded("iportal")) {
			return true;
		}
		try {
			Class.forName("qouteall.imm_ptl.core.portal.Portal", false,
				ClientPainterDoorwayTransition.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}

	private static void tickRecentCrossings() {
		if (recentCrossings.isEmpty()) return;
		Iterator<Map.Entry<BlockPos, Integer>> iterator = recentCrossings.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<BlockPos, Integer> entry = iterator.next();
			int ticks = entry.getValue() - 1;
			if (ticks <= 0) {
				iterator.remove();
			} else {
				entry.setValue(ticks);
			}
		}
	}

	private static void clearCorrection() {
		offset = Vec3d.ZERO;
		ageTicks = 0;
		durationTicks = 0;
	}

	private static void clearCrossingState(Object nextWorld) {
		world = nextWorld;
		lastEyePos = null;
		recentCrossings.clear();
		clearCorrection();
	}
}
