package dev.mapselect.role.spy;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.RoundLifecycleHooks;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.role.spy.SpyFeedPayload;
import dev.mapselect.network.role.spy.SpyListenPayload;
import dev.mapselect.network.role.spy.SpyListenStatePayload;
import dev.mapselect.network.role.spy.SpyStatusPayload;
import dev.mapselect.network.role.spy.SpyUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.bodyguard.BodyguardManager;
import dev.mapselect.role.altruist.AltruistManager;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.role.veteran.VeteranManager;
import dev.mapselect.task.ConversationTask;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpyManager {
	public static final int LISTEN_ENERGY_TICKS = 10 * 20;
	private static final int LISTEN_HEARTBEAT_GRACE_TICKS = 15;
	private static final int LISTEN_RECHARGE_INTERVAL_TICKS = 2;
	private static final double BUG_VOICE_RANGE = 8.0D;
	private static final Map<UUID, Bug> bugsBySpy = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> listenEnergyBySpy = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> listenRequestedUntil = new ConcurrentHashMap<>();
	private static final Set<UUID> activelyListening = ConcurrentHashMap.newKeySet();
	private static final Map<String, Long> nextLineTick = new ConcurrentHashMap<>();
	private static int tickGate;

	private SpyManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(SpyUsePayload.ID, SpyUsePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SpyListenPayload.ID, SpyListenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpyFeedPayload.ID, SpyFeedPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpyListenStatePayload.ID, SpyListenStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpyStatusPayload.ID, SpyStatusPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpyUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryBug(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(SpyListenPayload.ID,
			(payload, context) -> context.server().execute(() ->
				requestListening(context.player(), payload.listening())));
		ServerTickEvents.END_WORLD_TICK.register(SpyManager::tick);
		AllowPlayerDeath.EVENT.register(SpyManager::recordDeathInteraction);
		RoundLifecycleHooks.clearWorld(SpyManager::clear);
	}

	private static void tryBug(ServerPlayerEntity spy) {
		if (spy == null || !(spy.getWorld() instanceof ServerWorld world)) return;
		if (PelicanManager.isStashed(spy) || !isSpy(spy) || !canUseHere(world, spy)
				|| !isPlayable(spy, spy)) return;
		Bug activeBug = bugsBySpy.get(spy.getUuid());
		if (activeBug != null && activeBug.expiresAtTick() > world.getTime()) {
			sendStatus(spy, activeBug, world.getTime());
			spy.sendMessage(Text.literal("Your current bug is still active."), true);
			return;
		}
		ServerPlayerEntity target = findTarget(spy);
		if (target == null) {
			double range = GexpressConfig.getSpyBugRange();
			spy.sendMessage(Text.literal(AbilityTargeting.lookTargetFailure(spy, world.getPlayers(),
				range, 0.0D, true,
				candidate -> candidate != spy && !PelicanManager.isStashed(candidate)
					&& isPlayable(candidate, spy), "bug")), true);
			return;
		}
		PlayerShopComponent shop = PlayerShopComponent.KEY.get(spy);
		int cost = GexpressConfig.getSpyBugCost();
		if (cost > 0 && shop.balance < cost) {
			spy.sendMessage(Text.literal("You need " + cost + " coins to plant a bug."), true);
			return;
		}
		if (VeteranManager.interceptAbilityInteraction(spy, target)) return;
		if (cost > 0) {
			shop.setBalance(shop.balance - cost);
			PlayerShopComponent.KEY.sync(spy);
		}
		long expires = world.getTime() + (long) GexpressConfig.getSpyBugDurationSeconds() * 20L;
		Bug bug = new Bug(target.getUuid(), expires, target.getName().getString());
		bugsBySpy.put(spy.getUuid(), bug);
		syncVisuals(world);
		sendStatus(spy, bug, world.getTime());
		send(spy, "Bug planted on " + target.getName().getString() + ".");
	}

	private static void requestListening(ServerPlayerEntity spy, boolean listening) {
		if (spy == null) return;
		UUID spyId = spy.getUuid();
		if (!listening) {
			listenRequestedUntil.remove(spyId);
			activelyListening.remove(spyId);
			return;
		}
		if (!(spy.getWorld() instanceof ServerWorld world) || !canListen(spy, activeBug(spyId, world))) return;
		listenRequestedUntil.put(spyId, world.getTime() + LISTEN_HEARTBEAT_GRACE_TICKS);
	}

	private static void tickListening(ServerWorld world) {
		long now = world.getTime();
		for (ServerPlayerEntity spy : world.getPlayers()) {
			UUID spyId = spy.getUuid();
			if (!isSpy(spy)) {
				listenEnergyBySpy.remove(spyId);
				listenRequestedUntil.remove(spyId);
				activelyListening.remove(spyId);
				continue;
			}

			int energy = listenEnergyBySpy.getOrDefault(spyId, LISTEN_ENERGY_TICKS);
			Bug bug = activeBug(spyId, world);
			boolean requested = listenRequestedUntil.getOrDefault(spyId, 0L) > now;
			boolean listening = requested && energy > 0 && canListen(spy, bug);
			if (listening) {
				energy--;
			} else if (!requested && energy < LISTEN_ENERGY_TICKS
					&& now % LISTEN_RECHARGE_INTERVAL_TICKS == 0L) {
				energy++;
			}

			energy = Math.max(0, Math.min(LISTEN_ENERGY_TICKS, energy));
			if (energy >= LISTEN_ENERGY_TICKS) listenEnergyBySpy.remove(spyId);
			else listenEnergyBySpy.put(spyId, energy);

			boolean audible = listening;
			if (audible) activelyListening.add(spyId);
			else activelyListening.remove(spyId);
			if (!requested || bug == null) listenRequestedUntil.remove(spyId);
			syncListenState(spy, energy, audible);
		}
		activelyListening.removeIf(spyId -> {
			ServerPlayerEntity spy = world.getServer().getPlayerManager().getPlayer(spyId);
			return spy == null || spy.getWorld() == world && !isSpy(spy);
		});
	}

	private static Bug activeBug(UUID spyId, ServerWorld world) {
		Bug bug = spyId == null ? null : bugsBySpy.get(spyId);
		return bug != null && world != null && bug.expiresAtTick() > world.getTime() ? bug : null;
	}

	private static boolean canListen(ServerPlayerEntity spy, Bug bug) {
		if (spy == null || bug == null || !(spy.getWorld() instanceof ServerWorld world)
				|| PelicanManager.isStashed(spy) || !isSpy(spy) || !canUseHere(world, spy)
				|| !isPlayable(spy, spy)) {
			return false;
		}
		ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(bug.targetId());
		return target != null && target.getWorld() == world && !PelicanManager.isStashed(target)
			&& isPlayable(target, spy);
	}

	private static void syncListenState(ServerPlayerEntity spy, int energy, boolean listening) {
		PayloadSync.sendIfChanged(spy, SpyListenStatePayload.ID,
			new SpyListenStatePayload(energy, LISTEN_ENERGY_TICKS, listening));
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;
		tickListening(world);
		if (++tickGate < 10) return;
		tickGate = 0;
		long now = world.getTime();
		Iterator<Map.Entry<UUID, Bug>> iterator = bugsBySpy.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Bug> entry = iterator.next();
			ServerPlayerEntity spy = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			Bug bug = entry.getValue();
			ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(bug.targetId());
			if (spy == null || target == null || spy.getWorld() != world || target.getWorld() != world
					|| bug.expiresAtTick() <= now || !isSpy(spy) || !DeadPlayerStatus.isLivingRoundParticipant(spy)
					|| !isPlayable(target, spy)) {
				iterator.remove();
			}
		}
		syncVisuals(world);
		syncStatuses(world);
		nextLineTick.entrySet().removeIf(entry -> entry.getValue() <= now - 20L * 10L);
	}

	public static void recordTask(PlayerEntity player, PlayerMoodComponent.Task task) {
		if (!(player instanceof ServerPlayerEntity target) || task == null) return;
		String action = switch (task) {
			case SLEEP -> "slept";
			case OUTSIDE -> "got fresh air";
			case EAT -> "ate";
			case DRINK -> "drank";
		};
		sendToSpies(target, target.getName().getString() + " " + action + ".");
	}

	public static void recordTask(PlayerEntity player, PlayerMoodComponent.TrainTask task) {
		if (ConversationTask.isConversation(task)) {
			if (player instanceof ServerPlayerEntity target) {
				sendToSpies(target, target.getName().getString() + " made small talk.");
			}
			return;
		}
		if (task != null) recordTask(player, task.getType());
	}

	public static void recordInteraction(ServerPlayerEntity actor, ServerPlayerEntity other) {
		if (actor == null || other == null || actor == other) return;
		if (!isPlayable(actor, actor) || !isPlayable(other, actor)) return;
		sendToSpies(actor, actor.getName().getString() + " interacted with " + other.getName().getString() + ".");
		BodyguardManager.recordInteraction(actor, other);
		AltruistManager.recordInteraction(actor, other);
	}

	/** Returns the remote Spies currently listening to a bug close enough to hear this speaker. */
	public static Set<UUID> bugVoiceReceivers(ServerWorld world, UUID speakerId, Vec3d speakerPos) {
		if (world == null || speakerId == null || speakerPos == null || activelyListening.isEmpty()) {
			return Set.of();
		}
		ServerPlayerEntity speaker = world.getServer().getPlayerManager().getPlayer(speakerId);
		if (speaker == null || speaker.getWorld() != world || !isPlayable(speaker, speaker)) return Set.of();

		double rangeSquared = BUG_VOICE_RANGE * BUG_VOICE_RANGE;
		Set<UUID> receivers = new java.util.HashSet<>();
		for (UUID spyId : Set.copyOf(activelyListening)) {
			if (speakerId.equals(spyId)) continue;
			ServerPlayerEntity spy = world.getServer().getPlayerManager().getPlayer(spyId);
			Bug bug = activeBug(spyId, world);
			if (!canListen(spy, bug)) continue;
			ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(bug.targetId());
			if (target == null || target.squaredDistanceTo(speakerPos) > rangeSquared) continue;
			// Nearby Spies already receive normal positional voice; duplicating it would cause an echo.
			if (spy.squaredDistanceTo(speakerPos) <= rangeSquared) continue;
			receivers.add(spyId);
		}
		return Set.copyOf(receivers);
	}

	private static boolean recordDeathInteraction(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (victim instanceof ServerPlayerEntity target && killer instanceof ServerPlayerEntity actor && actor != target) {
			recordInteraction(actor, target);
		}
		return true;
	}

	private static void sendToSpies(ServerPlayerEntity target, String line) {
		if (target == null || line == null || line.isBlank()) return;
		if (!DeadPlayerStatus.isLivingRoundParticipant(target) && !GexpressTestState.isRoleTester(target)) return;
		long now = target.getWorld().getTime();
		for (Map.Entry<UUID, Bug> entry : new LinkedHashMap<>(bugsBySpy).entrySet()) {
			Bug bug = entry.getValue();
			if (!target.getUuid().equals(bug.targetId()) || bug.expiresAtTick() <= now) continue;
			ServerPlayerEntity spy = target.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (spy == null || spy.getWorld() != target.getWorld() || !isSpy(spy)
					|| (!DeadPlayerStatus.isLivingRoundParticipant(spy) && !GexpressTestState.isRoleTester(spy))) {
				continue;
			}
			String throttleKey = spy.getUuid() + ":" + target.getUuid() + ":" + line;
			if (now < nextLineTick.getOrDefault(throttleKey, 0L)) continue;
			nextLineTick.put(throttleKey, now + 20L);
			send(spy, line);
		}
	}

	private static ServerPlayerEntity findTarget(ServerPlayerEntity spy) {
		double range = GexpressConfig.getSpyBugRange();
		return AbilityTargeting.findLookTarget(spy, spy.getServerWorld().getPlayers(), range, 0.0D, true,
			candidate -> !PelicanManager.isStashed(candidate) && isPlayable(candidate, spy));
	}

	private static void send(ServerPlayerEntity spy, String line) {
		if (spy != null && ServerPlayNetworking.canSend(spy, SpyFeedPayload.ID)) {
			ServerPlayNetworking.send(spy, new SpyFeedPayload(line));
		}
	}

	private static void syncStatuses(ServerWorld world) {
		long now = world.getTime();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!isSpy(player)) continue;
			Bug bug = bugsBySpy.get(player.getUuid());
			sendStatus(player, bug, now);
		}
	}

	private static void sendStatus(ServerPlayerEntity spy, Bug bug, long now) {
		if (spy == null) return;
		int remaining = bug == null ? 0 : (int) Math.max(0L, bug.expiresAtTick() - now);
		String targetName = remaining > 0 ? bug.targetName() : "";
		PayloadSync.sendIfChanged(spy, SpyStatusPayload.ID, new SpyStatusPayload(remaining, targetName));
	}

	private static boolean isSpy(PlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && (MapSelectRoles.SPY_ID.equals(role.identifier())
			|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.SPY_ID));
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

	private static void clear() {
		bugsBySpy.clear();
		listenEnergyBySpy.clear();
		listenRequestedUntil.clear();
		activelyListening.clear();
		nextLineTick.clear();
		tickGate = 0;
		PayloadSync.forgetAll(SpyStatusPayload.ID);
		PayloadSync.forgetAll(SpyListenStatePayload.ID);
	}

	private static void clear(World world) {
		clear();
		if (world instanceof ServerWorld serverWorld) {
			SpyBugComponent.KEY.get(serverWorld).clearAll();
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				sendStatus(player, null, serverWorld.getTime());
			}
		}
	}

	private static void syncVisuals(ServerWorld world) {
		Map<UUID, Long> targets = new LinkedHashMap<>();
		long now = world.getTime();
		for (Bug bug : bugsBySpy.values()) {
			if (bug == null || bug.expiresAtTick() <= now) continue;
			targets.merge(bug.targetId(), bug.expiresAtTick(), Math::max);
		}
		SpyBugComponent.KEY.get(world).replaceAll(targets);
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(new HashMap<>(bugsBySpy), new HashMap<>(nextLineTick),
			new HashMap<>(listenEnergyBySpy));
	}

	public static void restoreForTimeRewind(TimeState state) {
		clear();
		if (state == null) return;
		bugsBySpy.putAll(state.bugsBySpy());
		nextLineTick.putAll(state.nextLineTick());
		listenEnergyBySpy.putAll(state.listenEnergyBySpy());
	}

	public record Bug(UUID targetId, long expiresAtTick, String targetName) {}

	public record TimeState(Map<UUID, Bug> bugsBySpy, Map<String, Long> nextLineTick,
		Map<UUID, Integer> listenEnergyBySpy) {}
}
