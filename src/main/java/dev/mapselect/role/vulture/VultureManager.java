package dev.mapselect.role.vulture;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.network.AbilityCooldownSync;
import dev.mapselect.network.VultureEatPayload;
import dev.mapselect.network.VultureProgressPayload;
import dev.mapselect.network.VultureReleasePayload;
import dev.mapselect.network.VultureStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.guardian.GuardianAngelManager;
import dev.mapselect.role.medic.MedicShieldComponent;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VultureManager {
	private static final double EAT_RANGE = 3.15D;
	private static final int EAT_TIME_BONUS_TICKS = 30 * 20;
	private static final Map<UUID, UUID> vultureByStashed = new ConcurrentHashMap<>();
	private static final Map<UUID, Deque<UUID>> stashedByVulture = new ConcurrentHashMap<>();
	private static final Map<UUID, Set<UUID>> eatenByVulture = new ConcurrentHashMap<>();
	private static final Map<UUID, StashedState> stashedStates = new ConcurrentHashMap<>();
	private static final Map<UUID, ReleasePoint> lastKnownVulturePoint = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> eatCooldownUntilByPelican = new ConcurrentHashMap<>();
	private static final int PROGRESS_SYNC_INTERVAL_TICKS = 10;
	private static final int STASHED_STATE_SYNC_INTERVAL_TICKS = 10;
	private static int progressSyncTicker = 0;
	private static int stashedStateSyncTicker = 0;

	private VultureManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(VultureEatPayload.ID, VultureEatPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(VultureReleasePayload.ID, VultureReleasePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(VultureStatePayload.ID, VultureStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(VultureProgressPayload.ID, VultureProgressPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(VultureEatPayload.ID,
			(payload, context) -> context.server().execute(() -> tryEat(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(VultureReleasePayload.ID,
			(payload, context) -> context.server().execute(() -> releaseOne(context.player())));
		ServerTickEvents.END_WORLD_TICK.register(VultureManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			server.execute(() -> onDisconnect(handler.player, server)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> onJoin(handler.player)));
		AllowPlayerDeath.EVENT.register(VultureManager::allowDeath);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearRoundState());
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
			if (world instanceof ServerWorld serverWorld) {
				releaseAllInWorldAtCurrentPositions(serverWorld);
				clearProgress(serverWorld);
			}
			clearRoundState();
		});
	}

	private static void tryEat(ServerPlayerEntity vulture) {
		if (!canUse(vulture)) return;
		long cooldown = remainingEatCooldownTicks(vulture);
		if (cooldown > 0L) {
			AbilityCooldownSync.send(vulture, AbilityCooldownPayload.PELICAN_SWALLOW, cooldown,
				(long) GexpressConfig.getPelicanEatCooldownSeconds() * 20L, false);
			vulture.sendMessage(Text.literal("Pelican swallow ready in " + secondsCeil(cooldown) + "s."), true);
			return;
		}
		ServerPlayerEntity target = findLookTarget(vulture);
		if (target == null) {
			vulture.sendMessage(Text.literal("No living player close enough to swallow."), true);
			return;
		}
		if (stash(vulture, target)) {
			long cooldownTicks = (long) GexpressConfig.getPelicanEatCooldownSeconds() * 20L;
			eatCooldownUntilByPelican.put(vulture.getUuid(), vulture.getWorld().getTime() + cooldownTicks);
			AbilityCooldownSync.send(vulture, AbilityCooldownPayload.PELICAN_SWALLOW, cooldownTicks, cooldownTicks, false);
		}
	}

	private static ServerPlayerEntity findLookTarget(ServerPlayerEntity vulture) {
		return AbilityTargeting.findLookTarget(vulture, vulture.getServerWorld().getPlayers(), EAT_RANGE, 0.0D, true,
			candidate -> !isStashed(candidate) && isPlayable(candidate, vulture));
	}

	private static boolean stash(ServerPlayerEntity vulture, ServerPlayerEntity target) {
		UUID vultureId = vulture.getUuid();
		UUID targetId = target.getUuid();
		if (vultureByStashed.containsKey(targetId)) {
			vulture.sendMessage(Text.literal(target.getName().getString() + " is already stashed."), true);
			return false;
		}

		lastKnownVulturePoint.put(vultureId, ReleasePoint.from(vulture));
		stashedStates.put(targetId, new StashedState(target.interactionManager.getGameMode(), target.isInvisible()));
		vultureByStashed.put(targetId, vultureId);
		stashedByVulture.computeIfAbsent(vultureId, id -> new ArrayDeque<>()).addLast(targetId);
		eatenByVulture.computeIfAbsent(vultureId, id -> ConcurrentHashMap.newKeySet()).add(targetId);

		clearAttachedShields(target);
		target.stopRiding();
		target.setSneaking(false);
		target.setInvisible(true);
		target.changeGameMode(GameMode.SPECTATOR);
		target.teleport(vulture.getServerWorld(), vulture.getX(), vulture.getY(), vulture.getZ(),
			vulture.getYaw(), vulture.getPitch());
		target.networkHandler.sendPacket(new SetCameraEntityS2CPacket(vulture));
		ServerPlayNetworking.send(target, new VultureStatePayload(true, vultureId, vulture.getId()));

		vulture.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.9F, 0.65F);
		boolean addedTime = addEatTimeBonus(vulture);
		Text message = addedTime
			? progressText(vulture).copy().append(Text.literal(" Timer +30s."))
			: progressText(vulture);
		vulture.sendMessage(message, true);
		syncProgress(vulture, true);
		target.sendMessage(Text.literal("You were swallowed by the Pelican."), true);
		SpyManager.recordInteraction(vulture, target);
		tryEndForVulture(vulture);
		return true;
	}

	private static void clearAttachedShields(ServerPlayerEntity target) {
		if (target == null || !(target.getWorld() instanceof ServerWorld world)) return;
		MedicShieldComponent medicShields = MedicShieldComponent.KEY.getNullable(world);
		if (medicShields != null) medicShields.removeShield(target.getUuid());
		GuardianAngelManager.removeShield(target.getUuid(), world);
	}

	private static boolean addEatTimeBonus(ServerPlayerEntity vulture) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(vulture.getWorld());
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return false;
		GameTimeComponent.KEY.get(vulture.getWorld()).addTime(EAT_TIME_BONUS_TICKS);
		return true;
	}

	private static void releaseOne(ServerPlayerEntity vulture) {
		if (!isVulture(vulture) || isStashed(vulture)) return;
		Deque<UUID> belly = stashedByVulture.get(vulture.getUuid());
		if (belly == null || belly.isEmpty()) {
			vulture.sendMessage(Text.literal("Your belly is empty."), true);
			return;
		}
		UUID targetId = belly.peekLast();
		releasePlayer(targetId, vulture.getServer(), ReleasePoint.from(vulture), true);
		syncProgress(vulture, true);
		vulture.sendMessage(Text.literal("Released one player."), true);
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (victim instanceof ServerPlayerEntity vulture && isVulture(vulture)) {
			releaseAllForVulture(vulture.getUuid(), vulture.getServer(), ReleasePoint.from(vulture), true, false);
		}
		return true;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean keepBellyState = game != null
			&& (game.isRunning() || game.getGameStatus() == GameWorldComponent.GameStatus.STOPPING);
		if (!keepBellyState && !GexpressTestState.hasRoleTesters()) {
			releaseAllInWorldAtCurrentPositions(world);
			clearProgress(world);
			clearRoundState();
			return;
		}
		if (progressSyncTicker-- <= 0) {
			progressSyncTicker = PROGRESS_SYNC_INTERVAL_TICKS;
			syncProgressForWorld(world, keepBellyState || GexpressTestState.hasRoleTesters());
		}
		boolean syncStashedState = false;
		if (stashedStateSyncTicker-- <= 0) {
			stashedStateSyncTicker = STASHED_STATE_SYNC_INTERVAL_TICKS;
			syncStashedState = true;
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (isVulture(player)) {
				lastKnownVulturePoint.put(player.getUuid(), ReleasePoint.from(player));
			}
		}

		for (Map.Entry<UUID, UUID> entry : List.copyOf(vultureByStashed.entrySet())) {
			UUID targetId = entry.getKey();
			UUID vultureId = entry.getValue();
			ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetId);
			ServerPlayerEntity vulture = world.getServer().getPlayerManager().getPlayer(vultureId);
			if (target == null) {
				continue;
			}
			if (vulture == null || !isPlayable(vulture, vulture)) {
				releasePlayer(targetId, world.getServer(), lastKnownVulturePoint.get(vultureId), true);
				continue;
			}
			keepStashedWithVulture(target, vulture);
			if (syncStashedState) syncStashedCamera(target, vulture);
		}
	}

	private static void keepStashedWithVulture(ServerPlayerEntity target, ServerPlayerEntity vulture) {
		if (target.interactionManager.getGameMode() != GameMode.SPECTATOR) {
			target.changeGameMode(GameMode.SPECTATOR);
		}
		target.setInvisible(true);
		if (target.getWorld() != vulture.getWorld()
				|| target.squaredDistanceTo(vulture) > 4.0D) {
			target.teleport(vulture.getServerWorld(), vulture.getX(), vulture.getY(), vulture.getZ(),
				vulture.getYaw(), vulture.getPitch());
		}
	}

	private static void syncStashedCamera(ServerPlayerEntity target, ServerPlayerEntity vulture) {
		target.networkHandler.sendPacket(new SetCameraEntityS2CPacket(vulture));
	}

	private static void onDisconnect(ServerPlayerEntity player, MinecraftServer server) {
		UUID playerId = player.getUuid();
		if (stashedByVulture.containsKey(playerId)) {
			releaseAllForVulture(playerId, server, lastKnownVulturePoint.get(playerId), true);
		}
	}

	private static void onJoin(ServerPlayerEntity player) {
		if (!isStashed(player)) return;
		UUID vultureId = vultureByStashed.get(player.getUuid());
		ServerPlayerEntity vulture = vultureId == null || player.getServer() == null
			? null : player.getServer().getPlayerManager().getPlayer(vultureId);
		player.setInvisible(true);
		player.changeGameMode(GameMode.SPECTATOR);
		if (vulture != null && isPlayable(vulture, vulture)) {
			keepStashedWithVulture(player, vulture);
			syncStashedCamera(player, vulture);
		} else if (ServerPlayNetworking.canSend(player, VultureStatePayload.ID)) {
			ServerPlayNetworking.send(player, new VultureStatePayload(true, vultureId, -1));
		}
	}

	public static boolean handleMurderTick(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
			return false;
		}

		List<ServerPlayerEntity> aliveVultures = world.getPlayers(GameFunctions::isPlayerAliveAndSurvival).stream()
			.filter(VultureManager::isVulture)
			.toList();
		if (aliveVultures.isEmpty()) return false;

		PassiveMoney.grant(world, game);

		GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;
		if (!GameTimeComponent.KEY.get(world).hasTime()) {
			winStatus = GameFunctions.WinStatus.TIME;
		} else {
			for (ServerPlayerEntity vulture : aliveVultures) {
				if (hasMetWinCondition(vulture)) {
					game.setLooseEndWinner(vulture.getUuid());
					NeutralWinManager.announce(world, vulture, "announcement.win.gexpress.pelican",
						MapSelectRoles.VULTURE == null ? 0x6F8A24 : MapSelectRoles.VULTURE.color());
					winStatus = GameFunctions.WinStatus.LOOSE_END;
					break;
				}
			}
		}

		if (winStatus != GameFunctions.WinStatus.NONE) {
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), winStatus);
			GameFunctions.stopGame(world);
		}
		return true;
	}

	private static void tryEndForVulture(ServerPlayerEntity vulture) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(vulture.getWorld());
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return;
		if (!hasMetWinCondition(vulture)) return;
		game.setLooseEndWinner(vulture.getUuid());
		NeutralWinManager.announce(vulture.getServerWorld(), vulture, "announcement.win.gexpress.pelican",
			MapSelectRoles.VULTURE == null ? 0x6F8A24 : MapSelectRoles.VULTURE.color());
		GameRoundEndComponent.KEY.get(vulture.getWorld()).setRoundEndData(vulture.getServerWorld().getPlayers(),
			GameFunctions.WinStatus.LOOSE_END);
		GameFunctions.stopGame(vulture.getServerWorld());
	}

	private static boolean hasMetWinCondition(ServerPlayerEntity vulture) {
		Set<UUID> eaten = eatenByVulture.get(vulture.getUuid());
		int eatenCount = eaten == null ? 0 : eaten.size();
		int required = requiredEaten(vulture);
		if (eatenCount >= required) return true;
		int remaining = required - eatenCount;
		return aliveOtherParticipants(vulture) == 0 && deadOtherParticipants(vulture, eaten) > remaining;
	}

	private static Text progressText(ServerPlayerEntity vulture) {
		int eaten = eatenByVulture.getOrDefault(vulture.getUuid(), Set.of()).size();
		return Text.literal("Pelican: " + eaten + "/" + requiredEaten(vulture) + " players swallowed.");
	}

	private static int requiredEaten(ServerPlayerEntity vulture) {
		int players = Math.max(1, roundParticipantCount(vulture));
		return Math.max(1, (int) Math.floor(players * (GexpressConfig.getPelicanEatPercentage() / 100.0D)));
	}

	private static int roundParticipantCount(ServerPlayerEntity vulture) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(vulture.getWorld());
		if (game != null && !game.getRoles().isEmpty()) {
			return game.getRoles().size();
		}
		return vulture.getServerWorld().getPlayers().size();
	}

	private static Set<UUID> roundParticipants(ServerPlayerEntity vulture) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(vulture.getWorld());
		if (game != null && !game.getRoles().isEmpty()) {
			return Set.copyOf(game.getRoles().keySet());
		}
		Set<UUID> participants = ConcurrentHashMap.newKeySet();
		for (ServerPlayerEntity player : vulture.getServerWorld().getPlayers()) {
			participants.add(player.getUuid());
		}
		return participants;
	}

	private static int aliveOtherParticipants(ServerPlayerEntity vulture) {
		int alive = 0;
		for (UUID playerId : roundParticipants(vulture)) {
			if (playerId.equals(vulture.getUuid())) continue;
			ServerPlayerEntity player = vulture.getServer().getPlayerManager().getPlayer(playerId);
			if (player != null && !isStashed(player) && GameFunctions.isPlayerAliveAndSurvival(player)) alive++;
		}
		return alive;
	}

	private static int deadOtherParticipants(ServerPlayerEntity vulture, Set<UUID> eaten) {
		int dead = 0;
		for (UUID playerId : roundParticipants(vulture)) {
			if (playerId.equals(vulture.getUuid()) || (eaten != null && eaten.contains(playerId))) continue;
			ServerPlayerEntity player = vulture.getServer().getPlayerManager().getPlayer(playerId);
			if (player == null || !GameFunctions.isPlayerAliveAndSurvival(player)) dead++;
		}
		return dead;
	}

	private static void releaseAllInWorld(ServerWorld world) {
		for (UUID vultureId : List.copyOf(stashedByVulture.keySet())) {
			ReleasePoint point = lastKnownVulturePoint.get(vultureId);
			releaseAllForVulture(vultureId, world.getServer(), point, false);
		}
	}

	private static void releaseAllInWorldAtCurrentPositions(ServerWorld world) {
		for (UUID vultureId : List.copyOf(stashedByVulture.keySet())) {
			releaseAllForVulture(vultureId, world.getServer(), null, false);
		}
	}

	private static void releaseAllForVulture(UUID vultureId, MinecraftServer server, ReleasePoint point, boolean notify) {
		releaseAllForVulture(vultureId, server, point, notify, false);
	}

	private static void releaseAllForVulture(UUID vultureId, MinecraftServer server, ReleasePoint point,
			boolean notify, boolean releaseAsDead) {
		Deque<UUID> belly = stashedByVulture.get(vultureId);
		if (belly == null || belly.isEmpty()) return;
		for (UUID targetId : new ArrayList<>(belly)) {
			releasePlayer(targetId, server, point, notify, releaseAsDead);
		}
	}

	private static void releasePlayer(UUID targetId, MinecraftServer server, ReleasePoint point, boolean notify) {
		releasePlayer(targetId, server, point, notify, false);
	}

	private static void releasePlayer(UUID targetId, MinecraftServer server, ReleasePoint point,
			boolean notify, boolean releaseAsDead) {
		if (targetId == null) return;
		StashedState state = stashedStates.remove(targetId);
		UUID vultureId = vultureByStashed.remove(targetId);
		if (vultureId != null) {
			Deque<UUID> belly = stashedByVulture.get(vultureId);
			if (belly != null) {
				belly.remove(targetId);
				if (belly.isEmpty()) stashedByVulture.remove(vultureId);
			}
			Set<UUID> eaten = eatenByVulture.get(vultureId);
			if (eaten != null) {
				eaten.remove(targetId);
				if (eaten.isEmpty()) eatenByVulture.remove(vultureId);
			}
		}
		if (server == null) return;
		ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
		if (target == null) return;

		ReleasePoint releasePoint = point != null ? point : ReleasePoint.from(target);
		ServerWorld releaseWorld = releasePoint.world(server);
		if (releaseWorld == null) releaseWorld = target.getServerWorld();
		if (releaseAsDead) {
			target.changeGameMode(GameMode.SPECTATOR);
			target.setInvisible(false);
			TrainVoicePlugin.addPlayer(target.getUuid());
		} else {
			GameMode restoreMode = state != null && state.previousGameMode != GameMode.SPECTATOR
				? state.previousGameMode : GameMode.SURVIVAL;
			target.changeGameMode(restoreMode);
			target.setInvisible(state != null && state.previousInvisible());
			TrainVoicePlugin.resetPlayer(target.getUuid());
		}
		target.teleport(releaseWorld, releasePoint.x, releasePoint.y, releasePoint.z,
			releasePoint.yaw, releasePoint.pitch);
		target.networkHandler.sendPacket(new SetCameraEntityS2CPacket(target));
		ServerPlayNetworking.send(target, VultureStatePayload.clear());
		if (notify) {
			target.sendMessage(Text.literal(releaseAsDead
				? "The Pelican died and spat you out."
				: "The Pelican spat you out."), true);
		}
		if (server != null && vultureId != null) {
			ServerPlayerEntity vulture = server.getPlayerManager().getPlayer(vultureId);
			if (vulture != null) syncProgress(vulture, true);
		}
	}

	private static void removeMissingTarget(UUID targetId, UUID vultureId) {
		stashedStates.remove(targetId);
		vultureByStashed.remove(targetId);
		Deque<UUID> belly = stashedByVulture.get(vultureId);
		if (belly != null) {
			belly.remove(targetId);
			if (belly.isEmpty()) stashedByVulture.remove(vultureId);
		}
	}

	public static boolean isStashed(ServerPlayerEntity player) {
		return player != null && vultureByStashed.containsKey(player.getUuid());
	}

	public static boolean isStashed(PlayerEntity player) {
		return player != null && vultureByStashed.containsKey(player.getUuid());
	}

	public static boolean isStashed(UUID playerId) {
		return playerId != null && vultureByStashed.containsKey(playerId);
	}

	public static boolean releaseFromBelly(ServerPlayerEntity target, boolean notify) {
		if (target == null) return false;
		UUID targetId = target.getUuid();
		UUID vultureId = vultureByStashed.get(targetId);
		if (vultureId == null) return false;
		MinecraftServer server = target.getServer();
		releasePlayer(targetId, server, lastKnownVulturePoint.get(vultureId), notify);
		ServerPlayerEntity vulture = server == null ? null : server.getPlayerManager().getPlayer(vultureId);
		if (vulture != null) syncProgress(vulture, true);
		return true;
	}

	public static void clearForTimeRewind(ServerWorld world) {
		if (world == null) return;
		releaseAllInWorld(world);
		clearRoundState();
	}

	public static TimeState snapshotForTimeRewind() {
		Map<UUID, Deque<UUID>> stashedCopy = new ConcurrentHashMap<>();
		for (Map.Entry<UUID, Deque<UUID>> entry : stashedByVulture.entrySet()) {
			stashedCopy.put(entry.getKey(), new ArrayDeque<>(entry.getValue()));
		}
		Map<UUID, Set<UUID>> eatenCopy = new ConcurrentHashMap<>();
		for (Map.Entry<UUID, Set<UUID>> entry : eatenByVulture.entrySet()) {
			eatenCopy.put(entry.getKey(), Set.copyOf(entry.getValue()));
		}
		return new TimeState(
			Map.copyOf(vultureByStashed),
			stashedCopy,
			eatenCopy,
			Map.copyOf(stashedStates),
			Map.copyOf(lastKnownVulturePoint),
			Map.copyOf(eatCooldownUntilByPelican)
		);
	}

	public static void restoreForTimeRewind(ServerWorld world, TimeState state) {
		clearRoundState();
		if (world == null || state == null) return;
		vultureByStashed.putAll(state.vultureByStashed());
		for (Map.Entry<UUID, Deque<UUID>> entry : state.stashedByVulture().entrySet()) {
			stashedByVulture.put(entry.getKey(), new ArrayDeque<>(entry.getValue()));
		}
		for (Map.Entry<UUID, Set<UUID>> entry : state.eatenByVulture().entrySet()) {
			Set<UUID> set = ConcurrentHashMap.newKeySet();
			set.addAll(entry.getValue());
			eatenByVulture.put(entry.getKey(), set);
		}
		stashedStates.putAll(state.stashedStates());
		lastKnownVulturePoint.putAll(state.lastKnownVulturePoint());
		eatCooldownUntilByPelican.putAll(state.eatCooldownUntilByPelican());

		for (Map.Entry<UUID, UUID> entry : vultureByStashed.entrySet()) {
			ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			ServerPlayerEntity vulture = world.getServer().getPlayerManager().getPlayer(entry.getValue());
			if (target == null || vulture == null || vulture.getWorld() != world) continue;
			keepStashedWithVulture(target, vulture);
			target.networkHandler.sendPacket(new SetCameraEntityS2CPacket(vulture));
			ServerPlayNetworking.send(target, new VultureStatePayload(true, vulture.getUuid(), vulture.getId()));
		}
		syncProgressForWorld(world, true);
	}

	public static boolean shouldCancelVoice(UUID senderId, UUID receiverId) {
		if (senderId == null || receiverId == null || senderId.equals(receiverId)) return false;
		UUID senderVulture = vultureByStashed.get(senderId);
		UUID receiverVulture = vultureByStashed.get(receiverId);
		if (senderVulture != null) {
			return !receiverId.equals(senderVulture) && !senderVulture.equals(receiverVulture);
		}
		if (receiverVulture != null) {
			return !senderId.equals(receiverVulture);
		}
		return false;
	}

	public static Set<UUID> bellyVoiceReceivers(UUID senderId) {
		if (senderId == null) return Set.of();
		UUID vultureId = vultureByStashed.get(senderId);
		if (vultureId != null) {
			Set<UUID> receivers = ConcurrentHashMap.newKeySet();
			receivers.add(vultureId);
			Deque<UUID> belly = stashedByVulture.get(vultureId);
			if (belly != null) receivers.addAll(belly);
			receivers.remove(senderId);
			return receivers;
		}
		Deque<UUID> belly = stashedByVulture.get(senderId);
		if (belly == null || belly.isEmpty()) return Set.of();
		return Set.copyOf(belly);
	}

	public static boolean isVulture(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.VULTURE_ID.equals(role.identifier());
	}

	private static boolean canUse(ServerPlayerEntity player) {
		if (player == null || player.getWorld().isClient) return false;
		return !isStashed(player) && isVulture(player) && canUseHere(player.getWorld(), player)
			&& isPlayable(player, player);
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static boolean isPlayable(PlayerEntity player, PlayerEntity vulture) {
		if (GexpressTestState.isRoleTester(vulture)) return true;
		if (player instanceof ServerPlayerEntity serverPlayer) {
			return DeadPlayerStatus.isLivingRoundParticipant(serverPlayer);
		}
		return GameFunctions.isPlayerAliveAndSurvival(player);
	}

	private static void clearRoundState() {
		vultureByStashed.clear();
		stashedByVulture.clear();
		eatenByVulture.clear();
		stashedStates.clear();
		lastKnownVulturePoint.clear();
		eatCooldownUntilByPelican.clear();
		progressSyncTicker = 0;
		stashedStateSyncTicker = 0;
	}

	private static void syncProgressForWorld(ServerWorld world, boolean show) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (show && isVulture(player)) {
				syncProgress(player, true);
			} else {
				sendProgress(player, VultureProgressPayload.clear());
			}
		}
	}

	private static void syncProgress(ServerPlayerEntity vulture, boolean show) {
		if (vulture == null) return;
		int eaten = eatenByVulture.getOrDefault(vulture.getUuid(), Set.of()).size();
		sendProgress(vulture, new VultureProgressPayload(show, eaten, requiredEaten(vulture), bellyEntries(vulture)));
	}

	private static List<VultureProgressPayload.BellyEntry> bellyEntries(ServerPlayerEntity vulture) {
		if (vulture == null || vulture.getServer() == null) return List.of();
		Deque<UUID> belly = stashedByVulture.get(vulture.getUuid());
		if (belly == null || belly.isEmpty()) return List.of();
		List<VultureProgressPayload.BellyEntry> out = new ArrayList<>();
		for (UUID targetId : belly) {
			ServerPlayerEntity target = vulture.getServer().getPlayerManager().getPlayer(targetId);
			String name = target == null ? "Offline" : target.getName().getString();
			out.add(new VultureProgressPayload.BellyEntry(targetId, name));
		}
		return out;
	}

	private static void clearProgress(ServerWorld world) {
		if (world == null) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			sendProgress(player, VultureProgressPayload.clear());
		}
	}

	private static void sendProgress(ServerPlayerEntity player, VultureProgressPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, VultureProgressPayload.ID)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static long remainingEatCooldownTicks(ServerPlayerEntity player) {
		Long until = eatCooldownUntilByPelican.get(player.getUuid());
		if (until == null) return 0L;
		long remaining = until - player.getWorld().getTime();
		if (remaining <= 0L) {
			eatCooldownUntilByPelican.remove(player.getUuid());
			return 0L;
		}
		return remaining;
	}

	public static long reduceEatCooldown(ServerPlayerEntity player, long ticks) {
		if (player == null || ticks <= 0L) return remainingEatCooldownTicks(player);
		long remaining = remainingEatCooldownTicks(player);
		if (remaining <= 0L) return 0L;
		long next = Math.max(0L, remaining - ticks);
		if (next <= 0L) {
			eatCooldownUntilByPelican.remove(player.getUuid());
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.PELICAN_SWALLOW);
		} else {
			eatCooldownUntilByPelican.put(player.getUuid(), player.getWorld().getTime() + next);
			AbilityCooldownSync.send(player, AbilityCooldownPayload.PELICAN_SWALLOW, next,
				(long) GexpressConfig.getPelicanEatCooldownSeconds() * 20L, false);
		}
		return next;
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}

	public record StashedState(GameMode previousGameMode, boolean previousInvisible) {}

	public record TimeState(Map<UUID, UUID> vultureByStashed, Map<UUID, Deque<UUID>> stashedByVulture,
			Map<UUID, Set<UUID>> eatenByVulture, Map<UUID, StashedState> stashedStates,
			Map<UUID, ReleasePoint> lastKnownVulturePoint, Map<UUID, Long> eatCooldownUntilByPelican) {}

	public record ReleasePoint(RegistryKey<World> worldKey, double x, double y, double z, float yaw, float pitch) {
		private static ReleasePoint from(ServerPlayerEntity player) {
			return new ReleasePoint(player.getWorld().getRegistryKey(), player.getX(), player.getY(), player.getZ(),
				player.getYaw(), player.getPitch());
		}

		private ServerWorld world(MinecraftServer server) {
			return server.getWorld(worldKey);
		}
	}
}
