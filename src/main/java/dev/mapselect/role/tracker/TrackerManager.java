package dev.mapselect.role.tracker;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.RoundLifecycleHooks;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.tracker.TrackerStatePayload;
import dev.mapselect.network.role.tracker.TrackerUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityCooldowns;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.role.veteran.VeteranManager;
import dev.mapselect.role.pelican.PelicanManager;
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
		RoundLifecycleHooks.clearWorld(TrackerManager::clearAll);
	}

	public static void tryTrack(ServerPlayerEntity tracker) {
		if (tracker == null || !(tracker.getWorld() instanceof ServerWorld world)) return;
		if (PelicanManager.isStashed(tracker) || !isTracker(tracker)
				|| !canUseHere(world, tracker) || !isPlayable(tracker, tracker)) {
			return;
		}
		boolean creativeBypass = GexpressTestState.hasCreativeAbilityBypass(tracker);
		long remaining = creativeBypass ? 0L : cooldownRemaining(tracker);
		if (!creativeBypass && remaining > 0L) {
			AbilityCooldownSync.send(tracker, AbilityCooldownPayload.TRACKER_TRACK, remaining,
				(long) GexpressConfig.getTrackerCooldownSeconds() * 20L, false);
			tracker.sendMessage(Text.literal("Tracker ready in " + Math.max(1L, AbilityCooldowns.secondsCeil(remaining)) + "s."), true);
			return;
		}
		ServerPlayerEntity target = findTarget(tracker);
		if (target == null) {
			double range = GexpressConfig.getTrackerRange();
			tracker.sendMessage(Text.literal(AbilityTargeting.lookTargetFailure(tracker,
				tracker.getServerWorld().getPlayers(), range, 0.0D, true,
				candidate -> !PelicanManager.isStashed(candidate) && isPlayable(candidate, tracker), "track")), true);
			return;
		}
		if (VeteranManager.interceptAbilityInteraction(tracker, target)) return;

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
		if (creativeBypass) {
			AbilityCooldownSync.clear(tracker, AbilityCooldownPayload.TRACKER_TRACK);
		} else {
			AbilityCooldowns.start(cooldownUntil, tracker, cooldown);
			AbilityCooldownSync.send(tracker, AbilityCooldownPayload.TRACKER_TRACK, cooldown, cooldown, false);
		}
		sync(tracker);
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;
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
		return AbilityTargeting.findLookTarget(tracker, tracker.getServerWorld().getPlayers(), range, 0.0D, true,
			candidate -> !PelicanManager.isStashed(candidate) && isPlayable(candidate, tracker));
	}

	private static void sync(ServerPlayerEntity tracker) {
		if (tracker == null) return;
		Set<UUID> tracked = trackedByTracker.getOrDefault(tracker.getUuid(), new LinkedHashSet<>());
		PayloadSync.sendIfChanged(tracker, TrackerStatePayload.ID, new TrackerStatePayload(new ArrayList<>(tracked)));
	}

	private static void clearAll(World world) {
		trackedByTracker.clear();
		cooldownUntil.clear();
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				PayloadSync.sendIfChanged(player, TrackerStatePayload.ID, new TrackerStatePayload(List.of()));
			}
		}
	}

	private static long cooldownRemaining(ServerPlayerEntity player) {
		return AbilityCooldowns.remaining(cooldownUntil, player);
	}

	public static long reduceCooldown(ServerPlayerEntity player, long ticks) {
		long next = AbilityCooldowns.reduce(cooldownUntil, player, ticks);
		if (next <= 0L) {
			if (player != null) AbilityCooldownSync.clear(player, AbilityCooldownPayload.TRACKER_TRACK);
		} else {
			AbilityCooldownSync.send(player, AbilityCooldownPayload.TRACKER_TRACK, next,
				(long) GexpressConfig.getTrackerCooldownSeconds() * 20L, false);
		}
		return next;
	}

	private static boolean isTracker(PlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && (MapSelectRoles.TRACKER_ID.equals(role.identifier())
			|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.TRACKER_ID));
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

}
