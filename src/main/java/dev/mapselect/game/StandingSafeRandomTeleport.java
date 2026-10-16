package dev.mapselect.game;

import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.mapselect.MapSelect;
import dev.mapselect.block.FusedOrnamentBlockEntity;
import dev.mapselect.block.GoldLedgeBlock;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.weather.MapWeatherComponent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityPose;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Finds random map destinations that have full standing clearance and a walkable escape. */
public final class StandingSafeRandomTeleport {
	private static final Identifier WATHE_GOLD_LEDGE_ID = Identifier.of("wathe", "gold_ledge");
	private static final TagKey<net.minecraft.block.Block> UNSAFE_BLOCKS = TagKey.of(RegistryKeys.BLOCK,
		Identifier.of(MapSelect.MOD_ID, "unsafe_random_teleport"));
	private static final double BOX_EPSILON = 1.0E-7D;
	private static final double SUPPORT_INSET = 0.04D;
	private static final double SUPPORT_Y_OFFSET = 0.08D;
	private static final double MAX_SUPPORT_DROP = 1.0D;
	private static final double RESERVED_PADDING = 0.2D;
	private static final int RANDOM_AREA_POINTS = 192;
	private static final int TARGET_ATTEMPTS = 192;
	private static final int SEARCH_RADIUS = 12;
	private static final int MAX_EXIT_SEARCH_RADIUS = 32;
	private static final int[] EGRESS_Y_OFFSETS = {0, 1, -1};
	private static final int[][] SEARCH_NEIGHBORS = {
		{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
	};

	private StandingSafeRandomTeleport() {}

	/**
	 * Preflights one non-overlapping destination per player. An empty result means the whole allocation failed;
	 * callers should not partially teleport the supplied group.
	 */
	public static Map<UUID, Destination> preflight(ServerWorld world, List<ServerPlayerEntity> players) {
		if (world == null || players == null || players.isEmpty()) return Map.of();

		List<TargetPoint> points = targetPoints(world);
		if (points.isEmpty()) return Map.of();
		Box playArea = currentPlayArea(world);
		List<ServerPlayerEntity> allocationOrder = new ArrayList<>();
		for (ServerPlayerEntity player : players) {
			if (player != null && player.getWorld() == world && !allocationOrder.contains(player)) {
				allocationOrder.add(player);
			}
		}
		if (allocationOrder.size() != players.size()) return Map.of();
		Collections.shuffle(allocationOrder);

		Map<UUID, Destination> destinations = new LinkedHashMap<>();
		List<Box> reserved = new ArrayList<>();
		for (ServerPlayerEntity player : allocationOrder) {
			Destination destination = findDestination(world, player, points, playArea, reserved);
			if (destination == null) return Map.of();
			destinations.put(player.getUuid(), destination);
			reserved.add(standingBox(player, destination.position()));
		}
		return Map.copyOf(destinations);
	}

	/** Finds the closest full-height, supported air pocket around an anchor for safe ability exits. */
	public static Vec3d nearestStandingSafe(ServerWorld world, ServerPlayerEntity player, Vec3d anchor,
			int maxRadius) {
		if (world == null || player == null || anchor == null) return null;
		Box playArea = currentPlayArea(world);
		if (isSafe(world, player, anchor.x, anchor.y, anchor.z, playArea, List.of())) return anchor;

		BlockPos origin = BlockPos.ofFloored(anchor);
		int radius = Math.max(1, Math.min(MAX_EXIT_SEARCH_RADIUS, maxRadius));
		double maxDistanceSquared = radius * radius + 0.75D;
		Comparator<SearchNode> nearestFirst = Comparator.comparingDouble(node ->
			anchor.squaredDistanceTo(node.position(origin)));
		PriorityQueue<SearchNode> candidates = new PriorityQueue<>(nearestFirst);
		Set<SearchNode> queued = new HashSet<>();
		offerSearchNode(candidates, queued, new SearchNode(0, 0, 0, false));
		offerSearchNode(candidates, queued, new SearchNode(0, 0, 0, true));

		while (!candidates.isEmpty()) {
			SearchNode node = candidates.remove();
			Vec3d candidate = node.position(origin);
			if (anchor.squaredDistanceTo(candidate) > maxDistanceSquared) continue;
			if (isSafe(world, player, candidate.x, candidate.y, candidate.z, playArea, List.of())) {
				return candidate;
			}
			for (int[] step : SEARCH_NEIGHBORS) {
				SearchNode next = new SearchNode(node.dx() + step[0], node.dy() + step[1],
					node.dz() + step[2], node.halfY());
				if (Math.abs(next.dx()) > radius || Math.abs(next.dy()) > radius
						|| Math.abs(next.dz()) > radius) continue;
				if (anchor.squaredDistanceTo(next.position(origin)) > maxDistanceSquared) continue;
				offerSearchNode(candidates, queued, next);
			}
		}
		return null;
	}

	/** Expands the nearest-first exit search without ever falling back to an unchecked position. */
	public static Vec3d nearestStandingSafeExpanding(ServerWorld world, ServerPlayerEntity player,
			Vec3d anchor) {
		if (world == null || player == null || anchor == null) return null;
		int limit = exitSearchLimit(world, anchor);
		// The underlying priority queue is already nearest-first. Searching once to the
		// expanded limit avoids rescanning the same inner volume at 8, 16, and 32 blocks.
		return nearestStandingSafe(world, player, anchor, Math.max(1, limit));
	}

	/** Tests only whether a fixed standing player box intersects solid block collision. */
	public static boolean isStandingBlockClear(ServerWorld world, ServerPlayerEntity player, Vec3d position) {
		if (world == null || player == null || position == null) return false;
		Box target = standingBox(player, position).contract(BOX_EPSILON);
		return world.isBlockSpaceEmpty(player, target);
	}

	private static void offerSearchNode(PriorityQueue<SearchNode> candidates, Set<SearchNode> queued,
			SearchNode node) {
		if (queued.add(node)) candidates.add(node);
	}

	private static int exitSearchLimit(ServerWorld world, Vec3d anchor) {
		Box area = currentPlayArea(world);
		if (area == null) return MAX_EXIT_SEARCH_RADIUS;
		double x = Math.max(Math.abs(anchor.x - area.minX), Math.abs(area.maxX - anchor.x));
		double y = Math.max(Math.abs(anchor.y - area.minY), Math.abs(area.maxY - anchor.y));
		double z = Math.max(Math.abs(anchor.z - area.minZ), Math.abs(area.maxZ - anchor.z));
		int mapLimit = Math.max(1, MathHelper.ceil(Math.sqrt(x * x + y * y + z * z)) + 1);
		return Math.min(MAX_EXIT_SEARCH_RADIUS, mapLimit);
	}

	private static Destination findDestination(ServerWorld world, ServerPlayerEntity player,
			List<TargetPoint> points, Box playArea, List<Box> reserved) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int attempt = 0; attempt < TARGET_ATTEMPTS; attempt++) {
			TargetPoint target = points.get(random.nextInt(points.size()));
			int x = MathHelper.floor(target.x());
			int z = MathHelper.floor(target.z());
			int playerY = target.explicitY() ? MathHelper.floor(target.y()) : MathHelper.floor(player.getY());
			int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			Vec3d safe = safeAround(world, player, new BlockPos(x, playerY, z), playerY, topY,
				target.explicitY(), playArea, reserved);
			if (safe != null) return new Destination(safe, random.nextFloat() * 360.0F);
		}
		return null;
	}

	private static Vec3d safeAround(ServerWorld world, ServerPlayerEntity player, BlockPos origin, int playerY,
			int topY, boolean explicitY, Box playArea, List<Box> reserved) {
		for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
					BlockPos column = origin.add(dx, 0, dz);
					Vec3d safe = safeYInColumn(world, player, column.getX() + 0.5D, column.getZ() + 0.5D,
						playerY, topY, explicitY, playArea, reserved);
					if (safe != null) return safe;
				}
			}
		}
		return null;
	}

	private static Vec3d safeYInColumn(ServerWorld world, ServerPlayerEntity player, double x, double z,
			int playerY, int seedTopY, boolean explicitY, Box playArea, List<Box> reserved) {
		if (explicitY) {
			int minY = Math.max(world.getBottomY() + 1, playerY - 4);
			int maxY = Math.min(world.getTopY() - 2, playerY + 4);
			int[] seeds = {playerY, playerY + 1, playerY - 1, playerY + 2, playerY - 2};
			for (int seed : seeds) {
				if (seed >= minY && seed <= maxY && isSafe(world, player, x, seed, z, playArea, reserved)) {
					return new Vec3d(x, seed, z);
				}
			}
			for (int offset = 0; offset <= 4; offset++) {
				int up = playerY + offset;
				if (up >= minY && up <= maxY && isSafe(world, player, x, up, z, playArea, reserved)) {
					return new Vec3d(x, up, z);
				}
				int down = playerY - offset;
				if (offset != 0 && down >= minY && down <= maxY
						&& isSafe(world, player, x, down, z, playArea, reserved)) {
					return new Vec3d(x, down, z);
				}
			}
			return null;
		}

		int columnTopY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
			MathHelper.floor(x), MathHelper.floor(z));
		int minY = Math.max(world.getBottomY() + 1, Math.min(Math.min(playerY, seedTopY), columnTopY) - 18);
		int maxY = Math.min(world.getTopY() - 2, Math.max(Math.max(playerY, seedTopY), columnTopY) + 18);
		int[] seeds = {playerY, columnTopY, seedTopY, playerY + 1, playerY - 1, columnTopY + 1, columnTopY - 1};
		for (int seed : seeds) {
			if (seed >= minY && seed <= maxY && isSafe(world, player, x, seed, z, playArea, reserved)) {
				return new Vec3d(x, seed, z);
			}
		}
		for (int offset = 0; offset <= Math.max(maxY - playerY, playerY - minY); offset++) {
			int up = playerY + offset;
			if (up >= minY && up <= maxY && isSafe(world, player, x, up, z, playArea, reserved)) {
				return new Vec3d(x, up, z);
			}
			int down = playerY - offset;
			if (offset != 0 && down >= minY && down <= maxY
					&& isSafe(world, player, x, down, z, playArea, reserved)) {
				return new Vec3d(x, down, z);
			}
		}
		return null;
	}

	private static boolean isSafe(ServerWorld world, ServerPlayerEntity player, double x, double y, double z,
			Box playArea, List<Box> reserved) {
		Vec3d destination = new Vec3d(x, y, z);
		Box target = standingBox(player, destination);
		if (!isSafeBase(world, player, target, playArea, reserved)) return false;
		return hasWalkableEscape(world, player, destination, playArea);
	}

	private static boolean hasWalkableEscape(ServerWorld world, ServerPlayerEntity player, Vec3d origin,
			Box playArea) {
		for (Direction direction : Direction.Type.HORIZONTAL) {
			for (int yOffset : EGRESS_Y_OFFSETS) {
				Vec3d first = origin.add(direction.getOffsetX(), yOffset, direction.getOffsetZ());
				if (!isSafeBase(world, player, standingBox(player, first), playArea, List.of())) continue;
				for (Direction nextDirection : Direction.Type.HORIZONTAL) {
					for (int nextYOffset : EGRESS_Y_OFFSETS) {
						Vec3d second = first.add(nextDirection.getOffsetX(), nextYOffset,
							nextDirection.getOffsetZ());
						if (Math.abs(second.x - origin.x) < 0.25D && Math.abs(second.z - origin.z) < 0.25D) {
							continue;
						}
						if (isSafeBase(world, player, standingBox(player, second), playArea, List.of())) return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean isSafeBase(ServerWorld world, ServerPlayerEntity player, Box target, Box playArea,
			List<Box> reserved) {
		if (target.minY < world.getBottomY() || target.maxY > world.getTopY()) return false;
		if (!world.getWorldBorder().contains(target) || !inside(playArea, target)) return false;
		if (!world.isSpaceEmpty(player, target.contract(BOX_EPSILON))) return false;
		if (containsFluidOrUnsafeBlock(world, target)) return false;
		for (Box used : reserved) {
			if (used.expand(RESERVED_PADDING).intersects(target)) return false;
		}
		return hasFullSupport(world, target);
	}

	private static boolean containsFluidOrUnsafeBlock(ServerWorld world, Box target) {
		BlockPos min = BlockPos.ofFloored(target.minX + BOX_EPSILON, target.minY + BOX_EPSILON,
			target.minZ + BOX_EPSILON);
		BlockPos max = BlockPos.ofFloored(target.maxX - BOX_EPSILON, target.maxY - BOX_EPSILON,
			target.maxZ - BOX_EPSILON);
		for (BlockPos pos : BlockPos.iterate(min, max)) {
			BlockState state = world.getBlockState(pos);
			if (!state.getFluidState().isEmpty() || isUnsafe(world, pos, state)) return true;
		}
		return false;
	}

	private static boolean hasFullSupport(ServerWorld world, Box target) {
		double feetY = target.minY;
		double minX = target.minX + SUPPORT_INSET;
		double maxX = target.maxX - SUPPORT_INSET;
		double minZ = target.minZ + SUPPORT_INSET;
		double maxZ = target.maxZ - SUPPORT_INSET;
		double centerX = (target.minX + target.maxX) * 0.5D;
		double centerZ = (target.minZ + target.maxZ) * 0.5D;
		double[][] samples = {
			{centerX, centerZ},
			{minX, minZ},
			{minX, maxZ},
			{maxX, minZ},
			{maxX, maxZ}
		};
		for (double[] sample : samples) {
			if (!hasSupportAt(world, sample[0], feetY, sample[1])) return false;
		}
		return true;
	}

	private static boolean hasSupportAt(ServerWorld world, double x, double feetY, double z) {
		BlockPos supportPos = BlockPos.ofFloored(x, feetY - SUPPORT_Y_OFFSET, z);
		BlockState support = world.getBlockState(supportPos);
		if (!support.getFluidState().isEmpty() || isUnsafe(world, supportPos, support)) return false;
		VoxelShape shape = support.getCollisionShape(world, supportPos);
		if (shape.isEmpty()) return false;

		double localX = x - supportPos.getX();
		double localZ = z - supportPos.getZ();
		for (Box part : shape.getBoundingBoxes()) {
			double surfaceY = supportPos.getY() + part.maxY;
			if (surfaceY > feetY + SUPPORT_Y_OFFSET || surfaceY < feetY - MAX_SUPPORT_DROP) continue;
			if (localX >= part.minX - BOX_EPSILON && localX <= part.maxX + BOX_EPSILON
					&& localZ >= part.minZ - BOX_EPSILON && localZ <= part.maxZ + BOX_EPSILON) {
				return true;
			}
		}
		return false;
	}

	private static boolean isUnsafe(BlockState state) {
		if (state == null) return true;
		if (state.isIn(UNSAFE_BLOCKS) || state.getBlock() instanceof GoldLedgeBlock) return true;
		return WATHE_GOLD_LEDGE_ID.equals(Registries.BLOCK.getId(state.getBlock()));
	}

	private static boolean isUnsafe(ServerWorld world, BlockPos pos, BlockState state) {
		if (isUnsafe(state)) return true;
		return world.getBlockEntity(pos) instanceof FusedOrnamentBlockEntity ornament
			&& ornament.hasGoldLedgeCollision();
	}

	private static Box standingBox(ServerPlayerEntity player, Vec3d destination) {
		return player.getDimensions(EntityPose.STANDING).getBoxAt(destination);
	}

	private static boolean inside(Box area, Box target) {
		return area == null || (target.minX >= area.minX && target.maxX <= area.maxX
			&& target.minY >= area.minY && target.maxY <= area.maxY
			&& target.minZ >= area.minZ && target.maxZ <= area.maxZ);
	}

	private static List<TargetPoint> targetPoints(ServerWorld world) {
		List<TargetPoint> points = new ArrayList<>();
		MapVariablesWorldComponent map = MapVariablesWorldComponent.KEY.getNullable(world);
		Box playArea = map == null ? null : map.getPlayArea();
		Vec3i playAreaOffset = map == null ? null : map.getPlayAreaOffset();
		for (MapPreset.PosData spawn : MapPreset.randomSpawnsFrom(world)) {
			TargetPoint point = TargetPoint.from(spawn, playArea, playAreaOffset);
			if (point != null) points.add(point);
		}

		if (points.isEmpty()) {
			String currentMap = MapWeatherComponent.KEY.get(world).getCurrentMapName();
			if (currentMap != null && !currentMap.isBlank()) {
				try {
					MapPreset preset = PresetStorage.load(world.getServer(), currentMap);
					if (preset != null) {
						preset.normalize();
						if (preset.randomSpawnPositions != null) {
							for (MapPreset.PosData spawn : preset.randomSpawnPositions) {
								TargetPoint point = TargetPoint.from(spawn, preset);
								if (point != null) points.add(point);
							}
						}
					}
				} catch (IOException ignored) {
				}
			}
		}
		addBoxPoints(points, playArea, RANDOM_AREA_POINTS);
		return points;
	}

	private static void addBoxPoints(List<TargetPoint> points, Box area, int count) {
		if (area == null) return;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double width = Math.max(1.0D, area.maxX - area.minX);
		double depth = Math.max(1.0D, area.maxZ - area.minZ);
		for (int i = 0; i < count; i++) {
			points.add(new TargetPoint(
				area.minX + random.nextDouble() * width,
				area.minZ + random.nextDouble() * depth
			));
		}
	}

	private static Box currentPlayArea(ServerWorld world) {
		MapVariablesWorldComponent map = MapVariablesWorldComponent.KEY.getNullable(world);
		return map == null ? null : map.getPlayArea();
	}

	public record Destination(Vec3d position, float yaw) {}

	private record SearchNode(int dx, int dy, int dz, boolean halfY) {
		private Vec3d position(BlockPos origin) {
			return new Vec3d(origin.getX() + dx + 0.5D,
				origin.getY() + dy + (halfY ? 0.5D : 0.0D),
				origin.getZ() + dz + 0.5D);
		}
	}

	private record TargetPoint(double x, double y, double z, boolean explicitY) {
		private TargetPoint(double x, double z) {
			this(x, 0.0D, z, false);
		}

		private static TargetPoint from(MapPreset.PosData pos) {
			return pos == null ? null : new TargetPoint(pos.x, pos.y, pos.z, true);
		}

		private static TargetPoint from(MapPreset.PosData pos, MapPreset preset) {
			if (pos == null || preset == null || preset.playArea == null || preset.playAreaOffset == null) {
				return from(pos);
			}
			return from(pos, preset.playArea.toBox(), preset.playAreaOffset.toVec3i());
		}

		private static TargetPoint from(MapPreset.PosData pos, Box playArea, Vec3i playAreaOffset) {
			if (pos == null || playArea == null || playAreaOffset == null) return from(pos);
			Vec3d original = new Vec3d(pos.x, pos.y, pos.z);
			Vec3d translated = original.add(playAreaOffset.getX(), playAreaOffset.getY(), playAreaOffset.getZ());
			if (!playArea.contains(original) && playArea.contains(translated)) {
				return new TargetPoint(translated.x, translated.y, translated.z, true);
			}
			return from(pos);
		}
	}
}
