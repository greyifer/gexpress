package dev.mapselect.role.cupid;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.modifier.LoversManager;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.cupid.CupidStatePayload;
import dev.mapselect.network.role.cupid.CupidUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CupidManager {
	private static final int NO_PAIR_GRACE_TICKS = 30 * 20;
	private static final Map<UUID, UUID> FIRST_TARGETS = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> NO_PAIR_GRACE_UNTIL = new HashMap<>();
	private static int winCheckTicker;

	private CupidManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(CupidStatePayload.ID, CupidStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CupidUsePayload.ID, CupidUsePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CupidUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryUse(context.player(), payload)));
		ServerTickEvents.END_WORLD_TICK.register(CupidManager::tick);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clear(world));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear(world));
	}

	public static void reduceCooldown(ServerPlayerEntity player, long ticks) {
		if (player == null || ticks <= 0L) return;
		UUID id = player.getUuid();
		Long until = COOLDOWN_UNTIL.get(id);
		if (until == null) return;
		COOLDOWN_UNTIL.put(id, Math.max(0L, until - ticks));
	}

	private static void tryUse(ServerPlayerEntity cupid, CupidUsePayload payload) {
		if (cupid == null || !(cupid.getWorld() instanceof ServerWorld world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || !isCupid(game, cupid) || PelicanManager.isStashed(cupid)
				|| !canUseHere(world, cupid) || !isPlayable(cupid)) {
			return;
		}

		long now = world.getTime();
		long remaining = COOLDOWN_UNTIL.getOrDefault(cupid.getUuid(), 0L) - now;
		if (remaining > 0L && !GexpressTestState.hasCreativeAbilityBypass(cupid)) {
			AbilityCooldownSync.send(cupid, AbilityCooldownPayload.CUPID_LINK, remaining,
				GexpressConfig.getCupidPairCooldownSeconds() * 20L, false);
			return;
		}

		ServerPlayerEntity target = requestedTarget(cupid, world, payload == null ? null : payload.targetId());
		if (target == null && (payload == null || payload.targetId() == null)) {
			target = AbilityTargeting.findLookTarget(cupid, world.getPlayers(),
				GexpressConfig.getCupidRange(), 0.25D, true, candidate ->
					candidate != cupid && !PelicanManager.isStashed(candidate) && isPlayable(candidate));
		}
		if (target == null) {
			cupid.sendMessage(Text.literal("No living player close enough to charm.").formatted(Formatting.RED), true);
			return;
		}
		if (LoversManager.isLinked(target.getUuid())) {
			cupid.sendMessage(Text.literal(target.getName().getString() + " is already in love.")
				.formatted(Formatting.RED), true);
			return;
		}

		UUID firstId = FIRST_TARGETS.get(cupid.getUuid());
		if (firstId == null) {
			FIRST_TARGETS.put(cupid.getUuid(), target.getUuid());
			sendState(cupid, target);
			cupid.sendMessage(Text.literal("First lover selected: " + target.getName().getString() + ".")
				.formatted(Formatting.LIGHT_PURPLE), true);
			return;
		}
		if (firstId.equals(target.getUuid())) {
			cupid.sendMessage(Text.literal("Pick a different second lover.").formatted(Formatting.RED), true);
			return;
		}

		ServerPlayerEntity first = world.getServer().getPlayerManager().getPlayer(firstId);
		FIRST_TARGETS.remove(cupid.getUuid());
		sendState(cupid, null);
		if (first == null || !isPlayable(first) || LoversManager.isLinked(first.getUuid())) {
			cupid.sendMessage(Text.literal("Your first lover is no longer available.").formatted(Formatting.RED), true);
			return;
		}
		if (LoversManager.createPair(cupid, first, target)) {
			NO_PAIR_GRACE_UNTIL.remove(cupid.getUuid());
			if (!GexpressTestState.hasCreativeAbilityBypass(cupid)) {
				long total = GexpressConfig.getCupidPairCooldownSeconds() * 20L;
				COOLDOWN_UNTIL.put(cupid.getUuid(), now + total);
				AbilityCooldownSync.send(cupid, AbilityCooldownPayload.CUPID_LINK, total, total, false);
			}
		}
		sendState(cupid, null);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (++winCheckTicker < 20) return;
		winCheckTicker = 0;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return;
		syncCupidHud(world, game);
		if (killCupidsWithoutAlivePairs(world, game)) return;
		if (!hasMetWinCondition(world, game)) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!isCupid(game, player) || !isPlayable(player)) continue;
			game.setLooseEndWinner(player.getUuid());
			NeutralWinManager.announce(world, player, "announcement.win.gexpress.cupid",
				MapSelectRoles.CUPID == null ? 0xF06AA8 : MapSelectRoles.CUPID.color());
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(),
				GameFunctions.WinStatus.LOOSE_END);
			GameFunctions.stopGame(world);
			return;
		}
	}

	public static boolean handleMurderTick(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
			return false;
		}
		List<ServerPlayerEntity> aliveCupids = world.getPlayers(player ->
			isCupid(game, player) && isPlayable(player) && !PelicanManager.isStashed(player));
		if (aliveCupids.isEmpty()) return false;

		PassiveMoney.grant(world, game);
		syncCupidHud(world, game);
		if (killCupidsWithoutAlivePairs(world, game)) return true;

		GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;
		if (!GameTimeComponent.KEY.get(world).hasTime()) {
			winStatus = GameFunctions.WinStatus.TIME;
		} else if (hasMetWinCondition(world, game)) {
			ServerPlayerEntity winner = aliveCupids.getFirst();
			game.setLooseEndWinner(winner.getUuid());
			NeutralWinManager.announce(world, winner, "announcement.win.gexpress.cupid",
				MapSelectRoles.CUPID == null ? 0xF06AA8 : MapSelectRoles.CUPID.color());
			winStatus = GameFunctions.WinStatus.LOOSE_END;
		}

		if (winStatus != GameFunctions.WinStatus.NONE) {
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), winStatus);
			GameFunctions.stopGame(world);
		}
		return true;
	}

	private static boolean killCupidsWithoutAlivePairs(ServerWorld world, GameWorldComponent game) {
		if (LoversManager.alivePairCount(world) > 0) {
			NO_PAIR_GRACE_UNTIL.clear();
			return false;
		}
		boolean killed = false;
		long now = world.getTime();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!isCupid(game, player) || !isPlayable(player)) continue;
			Long graceUntil = NO_PAIR_GRACE_UNTIL.get(player.getUuid());
			if (graceUntil == null) {
				NO_PAIR_GRACE_UNTIL.put(player.getUuid(), now + NO_PAIR_GRACE_TICKS);
				player.sendMessage(Text.literal("No lovers remain. You have 30 seconds to make a new pair.")
					.formatted(Formatting.LIGHT_PURPLE), true);
				continue;
			}
			if (now < graceUntil) continue;
			player.sendMessage(Text.literal("No lovers remain.").formatted(Formatting.LIGHT_PURPLE), true);
			GameFunctions.killPlayer(player, true, null, GameConstants.DeathReasons.GENERIC);
			NO_PAIR_GRACE_UNTIL.remove(player.getUuid());
			killed = true;
		}
		return killed;
	}

	private static boolean hasMetWinCondition(ServerWorld world, GameWorldComponent game) {
		int required = requiredLinkedPlayers(world, game);
		if (required <= 0) return false;
		return livingLinkedNonCupidParticipants(world, game) >= required;
	}

	private static int requiredLinkedPlayers(ServerWorld world, GameWorldComponent game) {
		int eligible = eligibleNonCupidParticipants(world, game).size();
		int livingEligible = livingEligibleNonCupidParticipants(world, game).size();
		int maximumEven = livingEligible & ~1;
		if (maximumEven <= 0) return 0;
		int raw = (int) Math.floor(eligible * (GexpressConfig.getCupidLovePercentage() / 100.0D));
		int even = (Math.max(2, raw) + 1) & ~1;
		return Math.min(maximumEven, even);
	}

	private static int livingLinkedNonCupidParticipants(ServerWorld world, GameWorldComponent game) {
		Set<UUID> eligible = eligibleNonCupidParticipants(world, game);
		if (eligible.isEmpty()) return 0;
		Set<UUID> living = new HashSet<>();
		for (LoversManager.PairView pair : LoversManager.pairViews(world)) {
			ServerPlayerEntity first = world.getServer().getPlayerManager().getPlayer(pair.firstId());
			ServerPlayerEntity second = world.getServer().getPlayerManager().getPlayer(pair.secondId());
			if (first == null || second == null || !DeadPlayerStatus.isLivingRoundParticipant(first)
					|| !DeadPlayerStatus.isLivingRoundParticipant(second)
					|| PelicanManager.isStashed(first) || PelicanManager.isStashed(second)) continue;
			if (eligible.contains(pair.firstId())) living.add(pair.firstId());
			if (eligible.contains(pair.secondId())) living.add(pair.secondId());
		}
		return living.size() & ~1;
	}

	private static Set<UUID> livingEligibleNonCupidParticipants(ServerWorld world, GameWorldComponent game) {
		Set<UUID> eligible = eligibleNonCupidParticipants(world, game);
		if (eligible.isEmpty()) return Set.of();
		Set<UUID> living = new HashSet<>();
		for (UUID playerId : eligible) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
			if (player != null && isPlayable(player) && !PelicanManager.isStashed(player)) {
				living.add(playerId);
			}
		}
		return living;
	}

	private static Set<UUID> eligibleNonCupidParticipants(ServerWorld world, GameWorldComponent game) {
		Set<UUID> participants = roundParticipants(world, game);
		if (participants.isEmpty()) return Set.of();
		Set<UUID> eligible = new HashSet<>(participants);
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (isCupid(game, player)) eligible.remove(player.getUuid());
		}
		return eligible;
	}

	private static Set<UUID> roundParticipants(ServerWorld world, GameWorldComponent game) {
		if (game != null && !game.getRoles().isEmpty()) {
			return Set.copyOf(game.getRoles().keySet());
		}
		Set<UUID> participants = new HashSet<>();
		for (ServerPlayerEntity player : world.getPlayers()) participants.add(player.getUuid());
		return participants;
	}

	private static boolean isCupid(GameWorldComponent game, PlayerEntity player) {
		Role role = game == null || player == null ? null : game.getRole(player);
		return role != null && (MapSelectRoles.CUPID_ID.equals(role.identifier())
			|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.CUPID_ID));
	}

	private static boolean isPlayable(ServerPlayerEntity player) {
		return (GameFunctions.isPlayerAliveAndSurvival(player) && DeadPlayerStatus.isLivingRoundParticipant(player))
			|| GexpressTestState.isRoleTester(player);
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static ServerPlayerEntity requestedTarget(ServerPlayerEntity cupid, ServerWorld world, UUID targetId) {
		if (cupid == null || world == null || targetId == null) return null;
		ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetId);
		if (target == null) return null;
		return AbilityTargeting.canTarget(cupid, target, GexpressConfig.getCupidRange(), 0.25D, true,
			candidate -> candidate != cupid && !PelicanManager.isStashed(candidate) && isPlayable(candidate))
			? target : null;
	}

	private static void sendState(ServerPlayerEntity cupid, ServerPlayerEntity firstTarget) {
		if (cupid == null || !(cupid.getWorld() instanceof ServerWorld world)
				|| !ServerPlayNetworking.canSend(cupid, CupidStatePayload.ID)) {
			return;
		}
		List<CupidStatePayload.Pair> pairs = LoversManager.pairViews(world).stream()
			.map(pair -> new CupidStatePayload.Pair(pair.firstId(), pair.firstName(), pair.secondId(), pair.secondName()))
			.toList();
		ServerPlayNetworking.send(cupid, new CupidStatePayload(firstTarget != null,
			firstTarget == null ? null : firstTarget.getUuid(),
			firstTarget == null ? "" : firstTarget.getName().getString(),
			pairs, livingLinkedNonCupidParticipants(world, GameWorldComponent.KEY.getNullable(world)),
			requiredLinkedPlayers(world, GameWorldComponent.KEY.getNullable(world))));
	}

	private static void syncCupidHud(ServerWorld world, GameWorldComponent game) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!isCupid(game, player) || !isPlayable(player)) continue;
			UUID firstId = FIRST_TARGETS.get(player.getUuid());
			ServerPlayerEntity first = firstId == null ? null : world.getServer().getPlayerManager().getPlayer(firstId);
			sendState(player, first);
		}
	}

	private static void clear(World world) {
		FIRST_TARGETS.clear();
		COOLDOWN_UNTIL.clear();
		NO_PAIR_GRACE_UNTIL.clear();
		winCheckTicker = 0;
		if (!(world instanceof ServerWorld serverWorld)) return;
		for (ServerPlayerEntity player : serverWorld.getPlayers()) {
			if (ServerPlayNetworking.canSend(player, CupidStatePayload.ID)) {
				ServerPlayNetworking.send(player, CupidStatePayload.clear());
			}
		}
	}
}
