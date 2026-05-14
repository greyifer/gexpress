package dev.mapselect.role.scatterbrain;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.network.AbilityCooldownSync;
import dev.mapselect.network.ScatterBrainUsePayload;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import dev.mapselect.weather.MapWeatherComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScatterBrainManager {
	private static final Random RANDOM = new Random();
	private static final Identifier GOLD_LEDGE_ID = Identifier.of("wathe", "gold_ledge");
	private static final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();

	private ScatterBrainManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ScatterBrainUsePayload.ID, ScatterBrainUsePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ScatterBrainUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryScatter(context.player())));
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (world.getRegistryKey() != World.OVERWORLD) return;
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
			if ((game == null || !game.isRunning()) && !GexpressTestState.hasRoleTesters()) {
				cooldownUntil.clear();
			}
		});
	}

	private static void tryScatter(ServerPlayerEntity scatterBrain) {
		if (scatterBrain == null || !(scatterBrain.getWorld() instanceof ServerWorld world)) return;
		if (VultureManager.isStashed(scatterBrain) || !isScatterBrain(scatterBrain)
				|| !canUseHere(world, scatterBrain) || !isPlayable(scatterBrain, scatterBrain)) {
			return;
		}
		long remaining = cooldownRemaining(scatterBrain);
		if (remaining > 0L) {
			AbilityCooldownSync.send(scatterBrain, AbilityCooldownPayload.SCATTER_BRAIN_SCATTER, remaining,
				(long) GexpressConfig.getScatterBrainCooldownSeconds() * 20L, false);
			scatterBrain.sendMessage(Text.literal("Scatter ready in " + secondsCeil(remaining) + "s."), true);
			return;
		}

		List<ServerPlayerEntity> players = world.getPlayers(player ->
			!VultureManager.isStashed(player) && isPlayable(player, scatterBrain));
		if (players.isEmpty()) {
			scatterBrain.sendMessage(Text.literal("Scatter needs living players."), true);
			return;
		}

		List<TargetPoint> points = targetPoints(world);
		if (points.isEmpty()) {
			scatterBrain.sendMessage(Text.literal("Scatter Brain needs map spawn points or a play area."), true);
			return;
		}

		int scattered = 0;
		for (ServerPlayerEntity player : players) {
			Vec3d safe = findSafePoint(world, player, points);
			if (safe == null) continue;
			float yaw = RANDOM.nextFloat() * 360.0F;
			player.teleport(world, safe.x, safe.y, safe.z, yaw, player.getPitch());
			player.setVelocity(0.0D, 0.0D, 0.0D);
			player.velocityModified = true;
			scattered++;
		}

		if (scattered == 0) {
			scatterBrain.sendMessage(Text.literal("Scatter Brain could not find random RTP slots."), true);
			return;
		}

		long cooldown = (long) GexpressConfig.getScatterBrainCooldownSeconds() * 20L;
		cooldownUntil.put(scatterBrain.getUuid(), world.getTime() + cooldown);
		AbilityCooldownSync.send(scatterBrain, AbilityCooldownPayload.SCATTER_BRAIN_SCATTER, cooldown, cooldown, false);
		world.playSound(null, scatterBrain.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
			SoundCategory.PLAYERS, 1.0F, 0.7F);
		scatterBrain.sendMessage(Text.literal("Scatter Brain."), true);
	}

	private static List<TargetPoint> targetPoints(ServerWorld world) {
		List<TargetPoint> points = new ArrayList<>();
		String currentMap = MapWeatherComponent.KEY.get(world).getCurrentMapName();
		if (currentMap != null && !currentMap.isBlank()) {
			try {
				MapPreset preset = PresetStorage.load(world.getServer(), currentMap);
				if (preset != null) {
					if (preset.randomSpawnPositions != null) {
						for (MapPreset.PosData spawn : preset.randomSpawnPositions) {
							points.add(TargetPoint.from(spawn));
						}
					}
				}
			} catch (IOException ignored) {
			}
		}
		if (!points.isEmpty()) return points;

		for (MapPreset.PosData spawn : MapPreset.randomSpawnsFrom(world)) points.add(TargetPoint.from(spawn));
		if (points.isEmpty()) {
			MapVariablesWorldComponent map = MapVariablesWorldComponent.KEY.getNullable(world);
			if (map != null) addBoxPoints(points, map.getPlayArea(), 128);
		}
		return points;
	}

	private static void addBoxPoints(List<TargetPoint> points, Box area, int count) {
		if (area == null) return;
		double width = Math.max(1.0D, area.maxX - area.minX);
		double depth = Math.max(1.0D, area.maxZ - area.minZ);
		for (int i = 0; i < count; i++) {
			points.add(new TargetPoint(
				area.minX + RANDOM.nextDouble() * width,
				area.minZ + RANDOM.nextDouble() * depth
			));
		}
	}

	private static Vec3d findSafePoint(ServerWorld world, ServerPlayerEntity player, List<TargetPoint> points) {
		for (int attempt = 0; attempt < 160; attempt++) {
			TargetPoint target = points.get(RANDOM.nextInt(points.size()));
			int x = MathHelper.floor(target.x());
			int z = MathHelper.floor(target.z());
			int playerY = target.y() > world.getBottomY()
				? MathHelper.floor(target.y())
				: MathHelper.floor(player.getY());
			int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			Vec3d safe = safeAround(world, player, new BlockPos(x, playerY, z), playerY, topY);
			if (safe != null) return safe;
		}
		return null;
	}

	private static Vec3d safeAround(ServerWorld world, ServerPlayerEntity player, BlockPos origin, int playerY,
			int topY) {
		for (int radius = 0; radius <= 12; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
					BlockPos pos = origin.add(dx, 0, dz);
					double x = pos.getX() + 0.5D;
					double z = pos.getZ() + 0.5D;
					Vec3d safe = safeYInColumn(world, player, x, z, playerY, topY);
					if (safe != null) return safe;
				}
			}
		}
		return null;
	}

	private static Vec3d safeYInColumn(ServerWorld world, ServerPlayerEntity player, double x, double z, int playerY,
			int seedTopY) {
		int columnTopY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
		int minY = Math.max(world.getBottomY() + 1, Math.min(Math.min(playerY, seedTopY), columnTopY) - 18);
		int maxY = Math.min(world.getTopY() - 2, Math.max(Math.max(playerY, seedTopY), columnTopY) + 18);
		int[] seeds = { playerY, columnTopY, seedTopY, playerY + 1, playerY - 1, columnTopY + 1, columnTopY - 1 };
		for (int seed : seeds) {
			if (seed >= minY && seed <= maxY && isSafe(world, player, x, seed, z)) {
				return new Vec3d(x, seed, z);
			}
		}
		for (int offset = 0; offset <= Math.max(maxY - playerY, playerY - minY); offset++) {
			int up = playerY + offset;
			if (up >= minY && up <= maxY && isSafe(world, player, x, up, z)) return new Vec3d(x, up, z);
			int down = playerY - offset;
			if (offset != 0 && down >= minY && down <= maxY && isSafe(world, player, x, down, z)) {
				return new Vec3d(x, down, z);
			}
		}
		return null;
	}

	private static boolean isSafe(ServerWorld world, ServerPlayerEntity player, double x, double y, double z) {
		Box current = player.getBoundingBox();
		Box target = current.offset(x - player.getX(), y - player.getY(), z - player.getZ());
		return world.isSpaceEmpty(player, target.contract(1.0E-7D)) && hasValidSupport(world, target);
	}

	private static boolean hasValidSupport(ServerWorld world, Box target) {
		double supportY = target.minY - 0.08D;
		double minX = target.minX + 0.05D;
		double maxX = target.maxX - 0.05D;
		double minZ = target.minZ + 0.05D;
		double maxZ = target.maxZ - 0.05D;
		double centerX = (target.minX + target.maxX) * 0.5D;
		double centerZ = (target.minZ + target.maxZ) * 0.5D;

		boolean supported = false;
		double[][] samples = {
			{ centerX, centerZ },
			{ minX, minZ },
			{ minX, maxZ },
			{ maxX, minZ },
			{ maxX, maxZ }
		};
		for (double[] sample : samples) {
			BlockPos supportPos = BlockPos.ofFloored(sample[0], supportY, sample[1]);
			if (GOLD_LEDGE_ID.equals(Registries.BLOCK.getId(world.getBlockState(supportPos).getBlock()))) {
				return false;
			}
			if (!world.getBlockState(supportPos).getCollisionShape(world, supportPos).isEmpty()) {
				supported = true;
			}
		}
		return supported;
	}

	private static long cooldownRemaining(ServerPlayerEntity player) {
		Long until = cooldownUntil.get(player.getUuid());
		if (until == null) return 0L;
		long remaining = until - player.getWorld().getTime();
		if (remaining <= 0L) {
			cooldownUntil.remove(player.getUuid());
			return 0L;
		}
		return remaining;
	}

	public static long reduceCooldown(ServerPlayerEntity player, long ticks) {
		if (player == null || ticks <= 0L) return cooldownRemaining(player);
		long remaining = cooldownRemaining(player);
		if (remaining <= 0L) return 0L;
		long next = Math.max(0L, remaining - ticks);
		if (next <= 0L) {
			cooldownUntil.remove(player.getUuid());
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.SCATTER_BRAIN_SCATTER);
		} else {
			cooldownUntil.put(player.getUuid(), player.getWorld().getTime() + next);
			AbilityCooldownSync.send(player, AbilityCooldownPayload.SCATTER_BRAIN_SCATTER, next,
				(long) GexpressConfig.getScatterBrainCooldownSeconds() * 20L, false);
		}
		return next;
	}

	private static boolean isScatterBrain(PlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && MapSelectRoles.SCATTER_BRAIN_ID.equals(role.identifier());
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static boolean isPlayable(PlayerEntity player, PlayerEntity user) {
		if (GexpressTestState.isRoleTester(user)) return true;
		if (player instanceof ServerPlayerEntity serverPlayer) {
			return DeadPlayerStatus.isLivingRoundParticipant(serverPlayer);
		}
		return GameFunctions.isPlayerAliveAndSurvival(player);
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}

	private record TargetPoint(double x, double y, double z, float yaw, float pitch) {
		private TargetPoint(double x, double z) {
			this(x, 0.0D, z, 0.0F, 0.0F);
		}

		private static TargetPoint from(MapPreset.PosData pos) {
			return new TargetPoint(pos.x, pos.y, pos.z, pos.yaw, pos.pitch);
		}
	}
}
