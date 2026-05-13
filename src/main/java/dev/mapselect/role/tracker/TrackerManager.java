package dev.mapselect.role.tracker;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.network.AbilityCooldownSync;
import dev.mapselect.network.TrackerStatePayload;
import dev.mapselect.network.TrackerUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrackerManager {
	private static final Map<UUID, LinkedHashSet<UUID>> trackedByTracker = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();

	private TrackerManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(TrackerUsePayload.ID, TrackerUsePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TrackerStatePayload.ID, TrackerStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TrackerUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryTrack(context.player())));
		ServerTickEvents.END_WORLD_TICK.register(TrackerManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> sync(handler.player)));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearAll(world));
	}

	private static void tryTrack(ServerPlayerEntity tracker) {
		if (tracker == null || !(tracker.getWorld() instanceof ServerWorld world)) return;
		if (VultureManager.isStashed(tracker) || !isTracker(tracker)
				|| !canUseHere(world, tracker) || !isPlayable(tracker, tracker)) {
			return;
		}
		long remaining = cooldownRemaining(tracker);
		if (remaining > 0L) {
			AbilityCooldownSync.send(tracker, AbilityCooldownPayload.TRACKER_TRACK, remaining,
				(long) GexpressConfig.getTrackerCooldownSeconds() * 20L, false);
			tracker.sendMessage(Text.literal("Tracker ready in " + secondsCeil(remaining) + "s."), true);
			return;
		}
		ServerPlayerEntity target = findTarget(tracker);
		if (target == null) {
			tracker.sendMessage(Text.literal("No living player close enough to track."), true);
			return;
		}

		LinkedHashSet<UUID> tracked = trackedByTracker.computeIfAbsent(tracker.getUuid(), id -> new LinkedHashSet<>());
		if (!tracked.remove(target.getUuid())) {
			int max = GexpressConfig.getTrackerMaxTargets();
			while (tracked.size() >= max && !tracked.isEmpty()) {
				UUID oldest = tracked.iterator().next();
				tracked.remove(oldest);
			}
			tracked.add(target.getUuid());
			tracker.sendMessage(Text.literal("Tracking " + target.getName().getString() + "."), true);
			SpyManager.recordInteraction(tracker, target);
		} else {
			tracker.sendMessage(Text.literal("Stopped tracking " + target.getName().getString() + "."), true);
		}
		long cooldown = (long) GexpressConfig.getTrackerCooldownSeconds() * 20L;
		cooldownUntil.put(tracker.getUuid(), world.getTime() + cooldown);
		AbilityCooldownSync.send(tracker, AbilityCooldownPayload.TRACKER_TRACK, cooldown, cooldown, false);
		sync(tracker);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if ((game == null || !game.isRunning()) && !GexpressTestState.hasRoleTesters()) {
			clearAll(world);
			return;
		}
		for (ServerPlayerEntity tracker : world.getPlayers(TrackerManager::isTracker)) {
			LinkedHashSet<UUID> tracked = trackedByTracker.get(tracker.getUuid());
			if (tracked == null || tracked.isEmpty()) continue;
			boolean changed = tracked.removeIf(id -> {
				ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(id);
				return target == null || target.getWorld() != world || !DeadPlayerStatus.isLivingRoundParticipant(target);
			});
			if (changed) sync(tracker);
		}
	}

	private static ServerPlayerEntity findTarget(ServerPlayerEntity tracker) {
		double range = GexpressConfig.getTrackerRange();
		return AbilityTargeting.findLookTarget(tracker, tracker.getServerWorld().getPlayers(), range, 0.25D, true,
			candidate -> !VultureManager.isStashed(candidate) && isPlayable(candidate, tracker));
	}

	private static void sync(ServerPlayerEntity tracker) {
		if (tracker == null || !ServerPlayNetworking.canSend(tracker, TrackerStatePayload.ID)) return;
		Set<UUID> tracked = trackedByTracker.getOrDefault(tracker.getUuid(), new LinkedHashSet<>());
		ServerPlayNetworking.send(tracker, new TrackerStatePayload(new ArrayList<>(tracked)));
	}

	private static void clearAll(World world) {
		trackedByTracker.clear();
		cooldownUntil.clear();
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				if (ServerPlayNetworking.canSend(player, TrackerStatePayload.ID)) {
					ServerPlayNetworking.send(player, new TrackerStatePayload(List.of()));
				}
			}
		}
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
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.TRACKER_TRACK);
		} else {
			cooldownUntil.put(player.getUuid(), player.getWorld().getTime() + next);
			AbilityCooldownSync.send(player, AbilityCooldownPayload.TRACKER_TRACK, next,
				(long) GexpressConfig.getTrackerCooldownSeconds() * 20L, false);
		}
		return next;
	}

	private static boolean isTracker(PlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && MapSelectRoles.TRACKER_ID.equals(role.identifier());
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
}
