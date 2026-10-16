package dev.mapselect.game;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.level.LevelComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RoundParticipantTracker {
	private static final int SNAPSHOT_INTERVAL_TICKS = 20;
	private static final Map<RegistryKey<World>, Integer> ticksUntilNextSnapshot = new ConcurrentHashMap<>();

	private RoundParticipantTracker() {}

	public static void register() {
		GameEvents.ON_FINISH_INITIALIZE.register(RoundParticipantTracker::beginRound);
		GameEvents.ON_FINISH_FINALIZE.register(RoundParticipantTracker::finishRound);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> handleJoin(handler.player)));
		ServerTickEvents.END_WORLD_TICK.register(RoundParticipantTracker::tick);
	}

	public static boolean isRoundParticipant(World world, GameWorldComponent game, UUID playerId) {
		if (world == null || playerId == null) return false;
		RoundParticipantComponent component = RoundParticipantComponent.KEY.getNullable(world);
		if (component == null || !component.hasParticipants()) {
			return game != null && game.getRoles().containsKey(playerId);
		}
		return component.isParticipant(playerId);
	}

	public static boolean isRoundSpectator(World world, GameWorldComponent game, UUID playerId) {
		if (world == null || playerId == null) return false;
		RoundParticipantComponent component = RoundParticipantComponent.KEY.getNullable(world);
		if (component == null) return false;
		if (component.isSpectator(playerId)) return true;
		return component.hasParticipants() && !component.isParticipant(playerId)
			&& game != null
			&& (game.isRunning() || game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
				|| game.getGameStatus() == GameWorldComponent.GameStatus.STOPPING);
	}

	public static String snapshotName(World world, UUID playerId, String fallback) {
		RoundParticipantComponent component = world == null ? null : RoundParticipantComponent.KEY.getNullable(world);
		return component == null ? (fallback == null ? "" : fallback) : component.name(playerId, fallback);
	}

	public static int snapshotLevel(World world, UUID playerId, int fallback) {
		RoundParticipantComponent component = world == null ? null : RoundParticipantComponent.KEY.getNullable(world);
		return component == null ? Math.max(1, fallback) : component.level(playerId, fallback);
	}

	public static void captureSnapshot(ServerPlayerEntity player) {
		if (player == null || player.getWorld() == null) return;
		RoundParticipantComponent component = RoundParticipantComponent.KEY.getNullable(player.getWorld());
		LevelComponent levels = LevelComponent.KEY.getNullable(player.getWorld());
		if (component != null && component.captureSnapshot(player, levels)) {
			RoundParticipantComponent.KEY.sync(player.getWorld());
		}
	}

	public static void markSpectator(ServerPlayerEntity player) {
		if (player == null || player.getWorld() == null) return;
		RoundParticipantComponent component = RoundParticipantComponent.KEY.getNullable(player.getWorld());
		LevelComponent levels = LevelComponent.KEY.getNullable(player.getWorld());
		if (component != null) component.markSpectator(player, levels);
	}

	private static void beginRound(World rawWorld, GameWorldComponent game) {
		if (!(rawWorld instanceof ServerWorld world) || game == null) return;
		ticksUntilNextSnapshot.put(world.getRegistryKey(), 0);
		RoundParticipantComponent component = RoundParticipantComponent.KEY.get(world);
		LevelComponent levels = LevelComponent.KEY.getNullable(world);
		Set<UUID> roundPlayers = new LinkedHashSet<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player != null && game.getRoles().containsKey(player.getUuid())) {
				roundPlayers.add(player.getUuid());
			}
		}
		component.beginRound(world.getPlayers(), roundPlayers, levels);
	}

	private static void finishRound(World rawWorld, GameWorldComponent game) {
		if (!(rawWorld instanceof ServerWorld world)) return;
		ticksUntilNextSnapshot.remove(world.getRegistryKey());
		RoundParticipantComponent component = RoundParticipantComponent.KEY.get(world);
		component.finishRound(world.getPlayers(), LevelComponent.KEY.getNullable(world));
	}

	private static void handleJoin(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)) return;
		touchPlayer(world, player);
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (!isActiveGame(game)) return;
		RegistryKey<World> worldKey = world.getRegistryKey();
		int remaining = ticksUntilNextSnapshot.getOrDefault(worldKey, 0);
		if (remaining > 0) {
			ticksUntilNextSnapshot.put(worldKey, remaining - 1);
			return;
		}
		ticksUntilNextSnapshot.put(worldKey, SNAPSHOT_INTERVAL_TICKS - 1);
		RoundParticipantComponent component = RoundParticipantComponent.KEY.getNullable(world);
		LevelComponent levels = LevelComponent.KEY.getNullable(world);
		if (component == null) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			touchPlayer(world, player, component, game, levels);
		}
	}

	private static void touchPlayer(ServerWorld world, ServerPlayerEntity player) {
		if (world == null || player == null) return;
		RoundParticipantComponent component = RoundParticipantComponent.KEY.getNullable(world);
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		LevelComponent levels = LevelComponent.KEY.getNullable(world);
		touchPlayer(world, player, component, game, levels);
	}

	private static void touchPlayer(ServerWorld world, ServerPlayerEntity player, RoundParticipantComponent component,
			GameWorldComponent game, LevelComponent levels) {
		if (world == null || player == null || component == null || game == null) return;
		component.captureSnapshot(player, levels);
		if (!component.roundActive() || component.isParticipant(player.getUuid())) return;
		if (!isActiveGame(game)) return;
		component.markSpectator(player, levels);
		if (GameFunctions.isPlayerAliveAndSurvival(player)
				&& player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
			player.changeGameMode(GameMode.SPECTATOR);
		}
	}

	private static boolean isActiveGame(GameWorldComponent game) {
		return game != null
			&& (game.isRunning() || game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE);
	}
}
