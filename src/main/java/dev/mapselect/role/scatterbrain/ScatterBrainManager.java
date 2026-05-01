package dev.mapselect.role.scatterbrain;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
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
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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
		if (players.size() < 2) {
			scatterBrain.sendMessage(Text.literal("Scatter needs at least two players."), true);
			return;
		}

		List<TargetPoint> points = targetPoints(world);
		if (points.isEmpty()) {
			scatterBrain.sendMessage(Text.literal("Scatter Brain needs map spawn points or a play area."), true);
			return;
		}

		for (ServerPlayerEntity player : players) {
			BlockPos pos = findSafePoint(world, player, points);
			if (pos == null) continue;
			player.teleport(world, pos.getX() + 0.5D, player.getY(), pos.getZ() + 0.5D,
				player.getYaw(), player.getPitch());
			player.setVelocity(0.0D, 0.0D, 0.0D);
			player.velocityModified = true;
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
				if (preset != null && preset.randomSpawnPositions != null) {
					for (MapPreset.PosData spawn : preset.randomSpawnPositions) {
						points.add(new TargetPoint(spawn.x, spawn.z));
					}
				}
			} catch (IOException ignored) {
			}
		}
		if (!points.isEmpty()) return points;

		Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
		if (playArea == null) return points;
		for (int i = 0; i < 96; i++) {
			double x = playArea.minX + RANDOM.nextDouble() * Math.max(1.0D, playArea.maxX - playArea.minX);
			double z = playArea.minZ + RANDOM.nextDouble() * Math.max(1.0D, playArea.maxZ - playArea.minZ);
			points.add(new TargetPoint(x, z));
		}
		return points;
	}

	private static BlockPos findSafePoint(ServerWorld world, ServerPlayerEntity player, List<TargetPoint> points) {
		for (int attempt = 0; attempt < 64; attempt++) {
			TargetPoint target = points.get(RANDOM.nextInt(points.size()));
			BlockPos origin = BlockPos.ofFloored(target.x(), player.getY(), target.z());
			BlockPos safe = safeAround(world, origin);
			if (safe != null) return safe;
		}
		return null;
	}

	private static BlockPos safeAround(ServerWorld world, BlockPos origin) {
		for (int radius = 0; radius <= 8; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
					BlockPos pos = origin.add(dx, 0, dz);
					if (isSafe(world, pos)) return pos;
				}
			}
		}
		return null;
	}

	private static boolean isSafe(ServerWorld world, BlockPos pos) {
		BlockState feet = world.getBlockState(pos);
		BlockState head = world.getBlockState(pos.up());
		BlockState below = world.getBlockState(pos.down());
		return feet.getCollisionShape(world, pos).isEmpty()
			&& head.getCollisionShape(world, pos.up()).isEmpty()
			&& !below.getCollisionShape(world, pos.down()).isEmpty();
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
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(user);
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}

	private record TargetPoint(double x, double z) {}
}
