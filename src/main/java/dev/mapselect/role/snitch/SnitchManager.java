package dev.mapselect.role.snitch;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SnitchManager {
	private static final int ENTITY_FLAGS_TRACKER_ID = 0;
	private static final byte ON_FIRE_FLAG = 1;
	private static final byte SNEAKING_FLAG = 1 << 1;
	private static final byte SPRINTING_FLAG = 1 << 3;
	private static final byte SWIMMING_FLAG = 1 << 4;
	private static final byte INVISIBLE_FLAG = 1 << 5;
	private static final byte GLOWING_FLAG = 1 << 6;

	private static final Map<UUID, Integer> lastTaskCounts = new HashMap<>();
	private static final Map<UUID, Integer> completedTasks = new HashMap<>();
	private static final Set<UUID> revealedSnitches = new HashSet<>();
	private static final Set<UUID> warnedSnitches = new HashSet<>();
	private static final Map<UUID, Set<UUID>> glowViewersBySnitch = new HashMap<>();

	private SnitchManager() {}

	public static void register() {
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
			if (previous != null && previous > 0 && current < previous && !revealedSnitches.contains(id)) {
				int completed = completedTasks.merge(id, previous - current, Integer::sum);
				if (completed >= GexpressConfig.getSnitchTasksRequired()) {
					clearSnitchGlow(player, world);
					revealKillers(player, game, world);
				} else {
					player.sendMessage(Text.literal("Snitch tasks: " + completed + "/"
						+ GexpressConfig.getSnitchTasksRequired()).formatted(Formatting.YELLOW), true);
					if (completed >= GexpressConfig.getSnitchTasksRequired() - 1) {
						warnKillers(player, game, world);
					}
				}
			}
		}

		refreshWarnedGlows(world, game, presentSnitches);
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
		MutableText message = Text.literal("Snitch reveal: ").formatted(Formatting.GOLD);
		boolean first = true;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!isPlayable(player) || !isKillerTeam(game, player)) continue;
			if (!first) message.append(Text.literal(", ").formatted(Formatting.GRAY));
			first = false;
			message.append(player.getName().copy().formatted(Formatting.RED));
			message.append(Text.literal(" (").formatted(Formatting.GRAY));
			message.append(roleName(game.getRole(player)).formatted(Formatting.RED));
			message.append(Text.literal(")").formatted(Formatting.GRAY));
		}
		if (first) {
			message.append(Text.literal("no living killers found").formatted(Formatting.GRAY));
		}
		snitch.sendMessage(message, false);
	}

	private static void warnKillers(ServerPlayerEntity snitch, GameWorldComponent game, ServerWorld world) {
		if (!warnedSnitches.add(snitch.getUuid())) return;
		MutableText warning = snitch.getName().copy().formatted(Formatting.YELLOW);
		warning.append(Text.literal(" is the Snitch and is one task away from revealing you.").formatted(Formatting.RED));
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player == snitch || !isPlayable(player) || !isKillerTeam(game, player)) continue;
			player.sendMessage(warning, false);
			sendGlow(player, snitch, true);
			glowViewersBySnitch.computeIfAbsent(snitch.getUuid(), id -> new HashSet<>()).add(player.getUuid());
		}
	}

	private static void refreshWarnedGlows(ServerWorld world, GameWorldComponent game, Set<UUID> presentSnitches) {
		for (UUID snitchId : Set.copyOf(warnedSnitches)) {
			ServerPlayerEntity snitch = world.getServer().getPlayerManager().getPlayer(snitchId);
			if (snitch == null || !presentSnitches.contains(snitchId) || !isPlayable(snitch)
					|| revealedSnitches.contains(snitchId)) {
				if (snitch != null) clearSnitchGlow(snitch, world);
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
			sendGlow(player, snitch, true);
		}

		Set<UUID> previousViewers = glowViewersBySnitch.computeIfAbsent(snitch.getUuid(), id -> new HashSet<>());
		for (UUID viewerId : Set.copyOf(previousViewers)) {
			if (currentViewers.contains(viewerId)) continue;
			ServerPlayerEntity viewer = world.getServer().getPlayerManager().getPlayer(viewerId);
			if (viewer != null) sendGlow(viewer, snitch, false);
		}
		previousViewers.clear();
		previousViewers.addAll(currentViewers);
	}

	private static void clearSnitchGlow(ServerPlayerEntity snitch, ServerWorld world) {
		Set<UUID> viewers = glowViewersBySnitch.remove(snitch.getUuid());
		if (viewers == null) return;
		for (UUID viewerId : viewers) {
			ServerPlayerEntity viewer = world.getServer().getPlayerManager().getPlayer(viewerId);
			if (viewer != null) sendGlow(viewer, snitch, false);
		}
	}

	private static void clearWorld(ServerWorld world) {
		for (UUID snitchId : Set.copyOf(glowViewersBySnitch.keySet())) {
			ServerPlayerEntity snitch = world.getServer().getPlayerManager().getPlayer(snitchId);
			if (snitch != null) clearSnitchGlow(snitch, world);
		}
	}

	private static void sendGlow(ServerPlayerEntity viewer, ServerPlayerEntity target, boolean glowing) {
		byte flags = entityFlags(target, glowing || target.isGlowing());
		viewer.networkHandler.sendPacket(new EntityTrackerUpdateS2CPacket(target.getId(), List.of(
			new DataTracker.SerializedEntry<>(ENTITY_FLAGS_TRACKER_ID, TrackedDataHandlerRegistry.BYTE, flags)
		)));
	}

	private static byte entityFlags(ServerPlayerEntity entity, boolean glowing) {
		byte flags = 0;
		if (entity.isOnFire()) flags |= ON_FIRE_FLAG;
		if (entity.isSneaking()) flags |= SNEAKING_FLAG;
		if (entity.isSprinting()) flags |= SPRINTING_FLAG;
		if (entity.isSwimming()) flags |= SWIMMING_FLAG;
		if (entity.isInvisible()) flags |= INVISIBLE_FLAG;
		if (glowing) flags |= GLOWING_FLAG;
		return flags;
	}

	private static boolean isSnitch(GameWorldComponent game, ServerPlayerEntity player) {
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.SNITCH_ID.equals(role.identifier());
	}

	private static boolean isKillerTeam(GameWorldComponent game, ServerPlayerEntity player) {
		Role role = game.getRole(player);
		return role != null && (role.canUseKiller() || game.canUseKillerFeatures(player));
	}

	private static MutableText roleName(Role role) {
		if (role == null || role.identifier() == null) return Text.literal("Unknown");
		return Text.translatable("announcement.role." + role.identifier().getNamespace()
			+ "." + role.identifier().getPath());
	}

	private static boolean isPlayable(ServerPlayerEntity player) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(player);
	}

	public static TimeState snapshotForTimeRewind() {
		Map<UUID, Set<UUID>> glows = new HashMap<>();
		for (Map.Entry<UUID, Set<UUID>> entry : glowViewersBySnitch.entrySet()) {
			glows.put(entry.getKey(), new HashSet<>(entry.getValue()));
		}
		return new TimeState(
			new HashMap<>(lastTaskCounts),
			new HashMap<>(completedTasks),
			new HashSet<>(revealedSnitches),
			new HashSet<>(warnedSnitches),
			glows
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
	}

	private static void clear() {
		lastTaskCounts.clear();
		completedTasks.clear();
		revealedSnitches.clear();
		warnedSnitches.clear();
		glowViewersBySnitch.clear();
	}

	public record TimeState(Map<UUID, Integer> lastTaskCounts, Map<UUID, Integer> completedTasks,
			Set<UUID> revealedSnitches, Set<UUID> warnedSnitches, Map<UUID, Set<UUID>> glowViewersBySnitch) {}
}
