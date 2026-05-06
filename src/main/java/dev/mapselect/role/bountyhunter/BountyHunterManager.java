package dev.mapselect.role.bountyhunter;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.GexpressGameModes;
import dev.mapselect.network.BountyHunterStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class BountyHunterManager {
	private static final Random RANDOM = new Random();
	private static final Map<UUID, UUID> targetByHunter = new HashMap<>();
	private static final Map<UUID, Long> deadlineByHunter = new HashMap<>();
	private static final Map<UUID, Long> penaltyUntilByHunter = new HashMap<>();
	private static final Map<UUID, Long> nextMessageTickByHunter = new HashMap<>();
	private static int tickGate;

	private BountyHunterManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(BountyHunterStatePayload.ID, BountyHunterStatePayload.CODEC);
		ServerTickEvents.END_WORLD_TICK.register(BountyHunterManager::tick);
		AllowPlayerDeath.EVENT.register(BountyHunterManager::allowDeath);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearRoundState(world));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearRoundState(world));
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean active = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!active && !GexpressTestState.hasRoleTesters()) {
			clearRoundState(world);
			tickGate = 0;
			return;
		}
		if (++tickGate < 5) return;
		tickGate = 0;

		for (ServerPlayerEntity hunter : world.getPlayers()) {
			if (!isBountyHunter(hunter) || VultureManager.isStashed(hunter) || !isPlayable(hunter, hunter)) {
				if (targetByHunter.containsKey(hunter.getUuid())) sendClear(hunter);
				clearHunter(hunter.getUuid());
				continue;
			}
			updateHunter(world, game, hunter);
		}
	}

	private static void updateHunter(ServerWorld world, GameWorldComponent game, ServerPlayerEntity hunter) {
		UUID hunterId = hunter.getUuid();
		long now = world.getTime();
		Long penaltyUntil = penaltyUntilByHunter.get(hunterId);
		if (penaltyUntil != null) {
			long remaining = penaltyUntil - now;
			if (remaining > 0L) {
				targetByHunter.remove(hunterId);
				deadlineByHunter.remove(hunterId);
				if (now >= nextMessageTickByHunter.getOrDefault(hunterId, 0L)) {
					nextMessageTickByHunter.put(hunterId, now + 20L);
					sendClear(hunter);
				}
				return;
			}
			penaltyUntilByHunter.remove(hunterId);
			assignNewBounty(world, game, hunter);
			return;
		}

		UUID targetId = targetByHunter.get(hunterId);
		ServerPlayerEntity target = targetId == null ? null : world.getServer().getPlayerManager().getPlayer(targetId);
		if (!isValidTarget(game, hunter, target)) {
			assignNewBounty(world, game, hunter);
			return;
		}

		long deadline = deadlineByHunter.getOrDefault(hunterId, now);
		if (now >= deadline) {
			startPenalty(world, hunter);
			return;
		}
		if (now >= nextMessageTickByHunter.getOrDefault(hunterId, 0L)) {
			nextMessageTickByHunter.put(hunterId, now + 20L);
			sendBountyState(hunter, target, deadline - now);
		}
	}

	private static void assignNewBounty(ServerWorld world, GameWorldComponent game, ServerPlayerEntity hunter) {
		List<ServerPlayerEntity> candidates = bountyCandidates(game, hunter);
		if (candidates.isEmpty()) {
			sendClear(hunter);
			clearHunter(hunter.getUuid());
			throttledMessage(world, hunter, Text.literal("No bounty available.").formatted(Formatting.GRAY));
			return;
		}
		ServerPlayerEntity target = candidates.get(RANDOM.nextInt(candidates.size()));
		long deadline = world.getTime() + (long) GexpressConfig.getBountyHunterBountyIntervalSeconds() * 20L;
		targetByHunter.put(hunter.getUuid(), target.getUuid());
		deadlineByHunter.put(hunter.getUuid(), deadline);
		nextMessageTickByHunter.put(hunter.getUuid(), world.getTime() + 20L);
		sendBountyState(hunter, target, deadline - world.getTime());
	}

	private static List<ServerPlayerEntity> bountyCandidates(GameWorldComponent game, ServerPlayerEntity hunter) {
		List<ServerPlayerEntity> out = new ArrayList<>();
		boolean amnesia = game != null && GexpressGameModes.isAmnesia(game);
		for (ServerPlayerEntity player : hunter.getServerWorld().getPlayers()) {
			if (player == hunter || VultureManager.isStashed(player) || !isPlayable(player, hunter)) continue;
			if (!amnesia && game != null && game.canUseKillerFeatures(player)) continue;
			out.add(player);
		}
		return out;
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (!(victim instanceof ServerPlayerEntity target) || !(killer instanceof ServerPlayerEntity hunter)) {
			return true;
		}
		if (!isBountyHunter(hunter)) return true;
		if (!target.getUuid().equals(targetByHunter.get(hunter.getUuid()))) return true;

		int reward = GexpressConfig.getBountyHunterRewardGold();
		if (reward > 0) {
			PlayerShopComponent.KEY.get(hunter).addToBalance(reward);
			PlayerShopComponent.KEY.sync(hunter);
		}
		hunter.sendMessage(Text.literal("Bounty claimed: +" + reward + " coins.").formatted(Formatting.GOLD), true);
		clearHunter(hunter.getUuid());
		sendClear(hunter);
		return true;
	}

	private static void startPenalty(ServerWorld world, ServerPlayerEntity hunter) {
		int ticks = Math.max(0, GexpressConfig.getBountyHunterFailCooldownSeconds() * 20);
		UUID hunterId = hunter.getUuid();
		targetByHunter.remove(hunterId);
		deadlineByHunter.remove(hunterId);
		sendClear(hunter);
		if (ticks > 0) {
			hunter.getItemCooldownManager().set(WatheItems.KNIFE, ticks);
			hunter.getItemCooldownManager().set(WatheItems.REVOLVER, ticks);
			penaltyUntilByHunter.put(hunterId, world.getTime() + ticks);
		} else {
			penaltyUntilByHunter.remove(hunterId);
		}
		hunter.sendMessage(Text.literal("Bounty failed. Weapons delayed.").formatted(Formatting.RED), true);
	}

	private static boolean isBountyHunter(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.BOUNTY_HUNTER_ID.equals(role.identifier());
	}

	private static boolean isValidTarget(GameWorldComponent game, ServerPlayerEntity hunter, ServerPlayerEntity target) {
		if (target == null || target == hunter || target.getWorld() != hunter.getWorld()) return false;
		if (VultureManager.isStashed(target) || !isPlayable(target, hunter)) return false;
		return game == null || GexpressGameModes.isAmnesia(game) || !game.canUseKillerFeatures(target);
	}

	private static boolean isPlayable(PlayerEntity player, PlayerEntity user) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(user);
	}

	private static void throttledMessage(ServerWorld world, ServerPlayerEntity hunter, Text message) {
		long now = world.getTime();
		if (now < nextMessageTickByHunter.getOrDefault(hunter.getUuid(), 0L)) return;
		nextMessageTickByHunter.put(hunter.getUuid(), now + 40L);
		hunter.sendMessage(message, true);
	}

	private static void sendBountyState(ServerPlayerEntity hunter, ServerPlayerEntity target, long remainingTicks) {
		if (hunter == null || target == null || !ServerPlayNetworking.canSend(hunter, BountyHunterStatePayload.ID)) return;
		ServerPlayNetworking.send(hunter, new BountyHunterStatePayload(true, target.getUuid(),
			target.getName().getString(), GexpressConfig.getBountyHunterRewardGold(), remainingTicks));
	}

	private static void sendClear(ServerPlayerEntity hunter) {
		if (hunter != null && ServerPlayNetworking.canSend(hunter, BountyHunterStatePayload.ID)) {
			ServerPlayNetworking.send(hunter, BountyHunterStatePayload.clear());
		}
	}

	private static void clearHunter(UUID hunterId) {
		targetByHunter.remove(hunterId);
		deadlineByHunter.remove(hunterId);
		penaltyUntilByHunter.remove(hunterId);
		nextMessageTickByHunter.remove(hunterId);
	}

	private static void clearRoundState(World world) {
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				sendClear(player);
			}
		}
		clearRoundState();
	}

	private static void clearRoundState() {
		targetByHunter.clear();
		deadlineByHunter.clear();
		penaltyUntilByHunter.clear();
		nextMessageTickByHunter.clear();
		tickGate = 0;
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(new HashMap<>(targetByHunter), new HashMap<>(deadlineByHunter),
			new HashMap<>(penaltyUntilByHunter), new HashMap<>(nextMessageTickByHunter));
	}

	public static void restoreForTimeRewind(TimeState state) {
		clearRoundState();
		if (state == null) return;
		targetByHunter.putAll(state.targetByHunter());
		deadlineByHunter.putAll(state.deadlineByHunter());
		penaltyUntilByHunter.putAll(state.penaltyUntilByHunter());
		nextMessageTickByHunter.putAll(state.nextMessageTickByHunter());
	}

	public record TimeState(Map<UUID, UUID> targetByHunter, Map<UUID, Long> deadlineByHunter,
			Map<UUID, Long> penaltyUntilByHunter, Map<UUID, Long> nextMessageTickByHunter) {}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}
}
