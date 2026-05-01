package dev.mapselect.role.snitch;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.SnitchProgressPayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SnitchManager {
	private static final int GLOW_REFRESH_INTERVAL_TICKS = 10;

	private static final Map<UUID, Integer> lastTaskCounts = new HashMap<>();
	private static final Map<UUID, Integer> completedTasks = new HashMap<>();
	private static final Set<UUID> revealedSnitches = new HashSet<>();
	private static final Set<UUID> warnedSnitches = new HashSet<>();
	private static final Map<UUID, Set<UUID>> glowViewersBySnitch = new HashMap<>();
	private static final Map<UUID, Set<UUID>> revealedKillerGlowsBySnitch = new HashMap<>();
	private static int ticksUntilGlowRefresh = 0;

	private SnitchManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SnitchProgressPayload.ID, SnitchProgressPayload.CODEC);
		ServerTickEvents.END_WORLD_TICK.register(SnitchManager::tick);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clear());
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
			if (world instanceof ServerWorld serverWorld) clearWorld(serverWorld);
			clear();
		});
	}

	public static boolean isRevealed(UUID playerId) {
		return playerId != null && revealedSnitches.contains(playerId);
	}

	public static boolean hasRevealedSnitches() {
		return !revealedSnitches.isEmpty();
	}

	public static void syncAll(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (isSnitch(game, player)) {
				syncSnitchInfo(player, game, world,
					completedTasks.getOrDefault(player.getUuid(), 0),
					GexpressConfig.getSnitchTasksRequired());
			}
		}
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean activeGame = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!activeGame && !GexpressTestState.hasRoleTesters()) {
			clearWorld(world);
			clear();
			return;
		}
		if (game == null) return;

		Set<UUID> presentSnitches = new HashSet<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!isSnitch(game, player) || !isPlayable(player)) continue;
			UUID id = player.getUuid();
			presentSnitches.add(id);
			int current = taskCount(player);
			Integer previous = lastTaskCounts.put(id, current);
			int completed = completedTasks.getOrDefault(id, 0);
			int required = GexpressConfig.getSnitchTasksRequired();
			if (previous != null && previous > 0 && current < previous && !revealedSnitches.contains(id)) {
				completed = completedTasks.merge(id, previous - current, Integer::sum);
				if (completed >= required) {
					clearSnitchGlow(player, world);
					revealKillers(player, game, world);
				}
			}
			int remaining = Math.max(0, required - completed);
			if (!revealedSnitches.contains(id) && remaining > 0
					&& remaining <= GexpressConfig.getSnitchWarningTasksRemaining()) {
				warnKillers(player, game, world);
			}
			syncSnitchInfo(player, game, world, Math.min(completed, required), required);
		}

		if (ticksUntilGlowRefresh > 0) {
			ticksUntilGlowRefresh--;
		} else {
			ticksUntilGlowRefresh = GLOW_REFRESH_INTERVAL_TICKS - 1;
			refreshWarnedGlows(world, game, presentSnitches);
			refreshRevealedKillerGlows(world, game, presentSnitches);
		}
		lastTaskCounts.keySet().removeIf(id -> !presentSnitches.contains(id));
		completedTasks.keySet().removeIf(id -> !presentSnitches.contains(id) && !revealedSnitches.contains(id));
	}

	private static int taskCount(ServerPlayerEntity player) {
		try {
			PlayerMoodComponent mood = PlayerMoodComponent.KEY.getNullable(player);
			return mood == null || mood.tasks == null ? 0 : mood.tasks.size();
		} catch (Throwable ignored) {
			return 0;
		}
	}

	private static void revealKillers(ServerPlayerEntity snitch, GameWorldComponent game, ServerWorld world) {
		revealedSnitches.add(snitch.getUuid());
		warnedSnitches.remove(snitch.getUuid());
		clearSnitchGlow(snitch, world);
		syncKillerGlowsToSnitch(snitch, game, world);
		syncSnitchInfo(snitch, game, world, GexpressConfig.getSnitchTasksRequired(),
			GexpressConfig.getSnitchTasksRequired());
	}

	private static void warnKillers(ServerPlayerEntity snitch, GameWorldComponent game, ServerWorld world) {
		if (!warnedSnitches.add(snitch.getUuid())) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player == snitch || !isPlayable(player) || !isKillerTeam(game, player)) continue;
			syncKillerWarningInfo(player, snitch);
			glowViewersBySnitch.computeIfAbsent(snitch.getUuid(), id -> new HashSet<>()).add(player.getUuid());
		}
	}

	private static void refreshWarnedGlows(ServerWorld world, GameWorldComponent game, Set<UUID> presentSnitches) {
		for (UUID snitchId : Set.copyOf(warnedSnitches)) {
			ServerPlayerEntity snitch = world.getServer().getPlayerManager().getPlayer(snitchId);
			if (snitch == null || !presentSnitches.contains(snitchId) || !isPlayable(snitch)
					|| revealedSnitches.contains(snitchId)) {
				if (snitch != null) clearSnitchGlow(snitch, world);
				else glowViewersBySnitch.remove(snitchId);
				warnedSnitches.remove(snitchId);
				continue;
			}
			syncGlowToKillers(snitch, game, world);
		}
	}

	private static void syncGlowToKillers(ServerPlayerEntity snitch, GameWorldComponent game, ServerWorld world) {
		Set<UUID> currentViewers = new HashSet<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player == snitch || !isPlayable(player) || !isKillerTeam(game, player)) continue;
			currentViewers.add(player.getUuid());
			syncKillerWarningInfo(player, snitch);
		}

		Set<UUID> previousViewers = glowViewersBySnitch.computeIfAbsent(snitch.getUuid(), id -> new HashSet<>());
		for (UUID viewerId : Set.copyOf(previousViewers)) {
			if (currentViewers.contains(viewerId)) continue;
			ServerPlayerEntity viewer = world.getServer().getPlayerManager().getPlayer(viewerId);
			if (viewer != null) {
				clearInfo(viewer);
			}
		}
		previousViewers.clear();
		previousViewers.addAll(currentViewers);
	}

	private static void refreshRevealedKillerGlows(ServerWorld world, GameWorldComponent game, Set<UUID> presentSnitches) {
		for (UUID snitchId : Set.copyOf(revealedSnitches)) {
			ServerPlayerEntity snitch = world.getServer().getPlayerManager().getPlayer(snitchId);
			if (snitch == null || !presentSnitches.contains(snitchId) || !isPlayable(snitch)) {
				if (snitch != null) clearKillerGlowsForSnitch(snitch, world);
				else revealedKillerGlowsBySnitch.remove(snitchId);
				revealedSnitches.remove(snitchId);
				continue;
			}
			syncKillerGlowsToSnitch(snitch, game, world);
			syncSnitchInfo(snitch, game, world, GexpressConfig.getSnitchTasksRequired(),
				GexpressConfig.getSnitchTasksRequired());
		}
	}

	private static void syncKillerGlowsToSnitch(ServerPlayerEntity snitch, GameWorldComponent game, ServerWorld world) {
		Set<UUID> currentTargets = new HashSet<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player == snitch || !isPlayable(player) || !isKillerTeam(game, player)) continue;
			currentTargets.add(player.getUuid());
		}

		Set<UUID> previousTargets = revealedKillerGlowsBySnitch.computeIfAbsent(snitch.getUuid(), id -> new HashSet<>());
		previousTargets.clear();
		previousTargets.addAll(currentTargets);
	}

	private static void clearSnitchGlow(ServerPlayerEntity snitch, ServerWorld world) {
		Set<UUID> viewers = glowViewersBySnitch.remove(snitch.getUuid());
		if (viewers == null) return;
		for (UUID viewerId : viewers) {
			ServerPlayerEntity viewer = world.getServer().getPlayerManager().getPlayer(viewerId);
			if (viewer != null) {
				clearInfo(viewer);
			}
		}
	}

	private static void clearKillerGlowsForSnitch(ServerPlayerEntity snitch, ServerWorld world) {
		revealedKillerGlowsBySnitch.remove(snitch.getUuid());
		clearInfo(snitch);
	}

	private static void syncSnitchInfo(ServerPlayerEntity player, GameWorldComponent game, ServerWorld world,
			int completed, int required) {
		boolean revealed = revealedSnitches.contains(player.getUuid());
		sendInfo(player, Math.min(completed, required), required, true,
			revealed ? killerInfoLines(game, world) : List.of());
	}

	private static void syncKillerWarningInfo(ServerPlayerEntity killer, ServerPlayerEntity snitch) {
		sendInfo(killer, 0, GexpressConfig.getSnitchTasksRequired(), false, List.of(
			new SnitchProgressPayload.InfoLine(
				snitch.getUuid(),
				snitch.getGameProfile().getName(),
				"Snitch",
				MapSelectRoles.SNITCH == null ? 0xE6B83D : MapSelectRoles.SNITCH.color()
			)
		));
	}

	private static void clearInfo(ServerPlayerEntity player) {
		sendInfo(player, 0, GexpressConfig.getSnitchTasksRequired(), false, List.of());
	}

	private static void sendInfo(ServerPlayerEntity player, int completed, int required, boolean showProgress,
			List<SnitchProgressPayload.InfoLine> lines) {
		if (player == null || !ServerPlayNetworking.canSend(player, SnitchProgressPayload.ID)) return;
		ServerPlayNetworking.send(player, new SnitchProgressPayload(
			Math.max(0, completed),
			Math.max(1, required),
			showProgress,
			List.copyOf(lines)
		));
	}

	private static List<SnitchProgressPayload.InfoLine> killerInfoLines(GameWorldComponent game, ServerWorld world) {
		List<SnitchProgressPayload.InfoLine> lines = new java.util.ArrayList<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!isPlayable(player) || !isKillerTeam(game, player)) continue;
			Role role = game.getRole(player);
			lines.add(new SnitchProgressPayload.InfoLine(
				player.getUuid(),
				player.getGameProfile().getName(),
				roleDisplayName(role),
				role == null ? 0xFF5555 : role.color()
			));
		}
		return lines;
	}

	private static void clearWorld(ServerWorld world) {
		for (UUID snitchId : Set.copyOf(glowViewersBySnitch.keySet())) {
			ServerPlayerEntity snitch = world.getServer().getPlayerManager().getPlayer(snitchId);
			if (snitch != null) clearSnitchGlow(snitch, world);
			else glowViewersBySnitch.remove(snitchId);
		}
		for (UUID snitchId : Set.copyOf(revealedKillerGlowsBySnitch.keySet())) {
			ServerPlayerEntity snitch = world.getServer().getPlayerManager().getPlayer(snitchId);
			if (snitch != null) clearKillerGlowsForSnitch(snitch, world);
			else revealedKillerGlowsBySnitch.remove(snitchId);
		}
	}

	private static boolean isSnitch(GameWorldComponent game, ServerPlayerEntity player) {
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.SNITCH_ID.equals(role.identifier());
	}

	private static boolean isKillerTeam(GameWorldComponent game, ServerPlayerEntity player) {
		Role role = game.getRole(player);
		return role != null && (role.canUseKiller() || game.canUseKillerFeatures(player));
	}

	private static String roleDisplayName(Role role) {
		if (role == null || role.identifier() == null) return "Unknown";
		if (role == WatheRoles.KILLER) return "Killer";
		if (role == WatheRoles.VIGILANTE) return "Vigilante";
		if (MapSelectRoles.BOMB_SPECIALIST_ID.equals(role.identifier())) return "Bomb Specialist";
		if (MapSelectRoles.THE_SILENT_ID.equals(role.identifier())) return "The Silent";
		if (MapSelectRoles.WARLOCK_ID.equals(role.identifier())) return "Warlock";
		if (MapSelectRoles.TRICKSTER_ID.equals(role.identifier())) return "Harlequin";
		if (MapSelectRoles.PUPPETMASTER_ID.equals(role.identifier())) return "Puppetmaster";
		if (MapSelectRoles.JUGGERNAUT_ID.equals(role.identifier())) return "Juggernaut";
		if (MapSelectRoles.VULTURE_ID.equals(role.identifier())) return "Pelican";
		String path = role.identifier().getPath().replace('_', ' ');
		StringBuilder out = new StringBuilder(path.length());
		boolean capitalize = true;
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			out.append(capitalize ? Character.toUpperCase(c) : c);
			capitalize = c == ' ';
		}
		return out.toString();
	}

	private static boolean isPlayable(ServerPlayerEntity player) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(player);
	}

	public static TimeState snapshotForTimeRewind() {
		Map<UUID, Set<UUID>> glows = new HashMap<>();
		for (Map.Entry<UUID, Set<UUID>> entry : glowViewersBySnitch.entrySet()) {
			glows.put(entry.getKey(), new HashSet<>(entry.getValue()));
		}
		Map<UUID, Set<UUID>> revealedGlows = new HashMap<>();
		for (Map.Entry<UUID, Set<UUID>> entry : revealedKillerGlowsBySnitch.entrySet()) {
			revealedGlows.put(entry.getKey(), new HashSet<>(entry.getValue()));
		}
		return new TimeState(
			new HashMap<>(lastTaskCounts),
			new HashMap<>(completedTasks),
			new HashSet<>(revealedSnitches),
			new HashSet<>(warnedSnitches),
			glows,
			revealedGlows
		);
	}

	public static void restoreForTimeRewind(ServerWorld world, TimeState state) {
		if (world != null) clearWorld(world);
		clear();
		if (state == null) return;
		lastTaskCounts.putAll(state.lastTaskCounts());
		completedTasks.putAll(state.completedTasks());
		revealedSnitches.addAll(state.revealedSnitches());
		warnedSnitches.addAll(state.warnedSnitches());
		for (Map.Entry<UUID, Set<UUID>> entry : state.glowViewersBySnitch().entrySet()) {
			glowViewersBySnitch.put(entry.getKey(), new HashSet<>(entry.getValue()));
		}
		for (Map.Entry<UUID, Set<UUID>> entry : state.revealedKillerGlowsBySnitch().entrySet()) {
			revealedKillerGlowsBySnitch.put(entry.getKey(), new HashSet<>(entry.getValue()));
		}
	}

	private static void clear() {
		lastTaskCounts.clear();
		completedTasks.clear();
		revealedSnitches.clear();
		warnedSnitches.clear();
		glowViewersBySnitch.clear();
		revealedKillerGlowsBySnitch.clear();
		ticksUntilGlowRefresh = 0;
	}

	public record TimeState(Map<UUID, Integer> lastTaskCounts, Map<UUID, Integer> completedTasks,
			Set<UUID> revealedSnitches, Set<UUID> warnedSnitches, Map<UUID, Set<UUID>> glowViewersBySnitch,
			Map<UUID, Set<UUID>> revealedKillerGlowsBySnitch) {}
}
