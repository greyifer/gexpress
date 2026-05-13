package dev.mapselect.role.guardian;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.GuardianAngelShieldStatePayload;
import dev.mapselect.network.GuardianAngelShieldUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.vulture.VultureManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GuardianAngelManager {
	private static final int SHIELD_DURATION_TICKS = 100;
	private static final int SYNC_INTERVAL_TICKS = 10;
	private static final double SHIELD_RANGE = 32.0D;

	private static UUID guardianId;
	private static boolean guardianUsed;
	private static final Map<UUID, Long> shieldUntilByTarget = new LinkedHashMap<>();
	private static int syncDelay;

	private GuardianAngelManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(GuardianAngelShieldUsePayload.ID, GuardianAngelShieldUsePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GuardianAngelShieldStatePayload.ID, GuardianAngelShieldStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GuardianAngelShieldUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryShield(context.player())));
		AllowPlayerDeath.EVENT.register(GuardianAngelManager::allowDeath);
		ServerTickEvents.END_WORLD_TICK.register(GuardianAngelManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> syncTo(handler.player)));
		GameEvents.ON_FINISH_INITIALIZE.register(GuardianAngelManager::onFinishInitialize);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
			clear();
			if (world instanceof ServerWorld serverWorld) syncAll(serverWorld);
		});
	}

	private static void onFinishInitialize(World world, GameWorldComponent game) {
		clear();
		if (!(world instanceof ServerWorld serverWorld) || game == null) return;

		List<ServerPlayerEntity> candidates = new ArrayList<>();
		for (ServerPlayerEntity player : serverWorld.getPlayers()) {
			if (!game.getRoles().containsKey(player.getUuid())
					|| VultureManager.isStashed(player)
					|| !GameFunctions.isPlayerAliveAndSurvival(player)) {
				continue;
			}
			Role role = game.getRole(player);
			if (!canBeGuardian(game, player, role)) continue;
			candidates.add(player);
		}
		if (candidates.isEmpty()) return;
		guardianId = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).getUuid();
	}

	private static boolean canBeGuardian(GameWorldComponent game, ServerPlayerEntity player, Role role) {
		if (GexpressConfig.canGuardianAngelPickNonInnocents()) return role != null;
		if (role == null || !role.isInnocent() || game.canUseKillerFeatures(player)) return false;
		return !isMafiaRole(role);
	}

	private static boolean isMafiaRole(Role role) {
		if (role == null || role.identifier() == null) return false;
		Identifier id = role.identifier();
		return MapSelectRoles.GODFATHER_ID.equals(id)
			|| MapSelectRoles.MAFIOSO_ID.equals(id)
			|| MapSelectRoles.JANITOR_ID.equals(id);
	}

	private static void tryShield(ServerPlayerEntity guardian) {
		if (guardian == null || guardian.getWorld().isClient || !guardian.getUuid().equals(guardianId)
				|| guardianUsed || !DeadPlayerStatus.isDeadRoundParticipant(guardian)) {
			return;
		}

		ServerPlayerEntity target = findLookTarget(guardian);
		if (target == null) return;

		guardianUsed = true;
		shieldUntilByTarget.put(target.getUuid(), guardian.getWorld().getTime() + SHIELD_DURATION_TICKS);
		syncAll(guardian.getServerWorld());
	}

	private static ServerPlayerEntity findLookTarget(ServerPlayerEntity guardian) {
		return AbilityTargeting.findLookTarget(guardian, guardian.getServerWorld().getPlayers(),
			SHIELD_RANGE, 0.0D, true, DeadPlayerStatus::isLivingRoundParticipant);
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (!(victim instanceof ServerPlayerEntity player)) return true;
		Long until = shieldUntilByTarget.get(player.getUuid());
		if (until == null || player.getWorld().getTime() >= until) {
			shieldUntilByTarget.remove(player.getUuid());
			return true;
		}
		syncAll(player.getServerWorld());
		return false;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (!DeadPlayerStatus.isRunningOrStopping(world)) {
			if (guardianId != null || guardianUsed || !shieldUntilByTarget.isEmpty()) {
				clear();
				syncAll(world);
			}
			return;
		}

		boolean changed = false;
		long now = world.getTime();
		for (Map.Entry<UUID, Long> entry : List.copyOf(shieldUntilByTarget.entrySet())) {
			ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (entry.getValue() <= now || target == null || !DeadPlayerStatus.isLivingRoundParticipant(target)) {
				shieldUntilByTarget.remove(entry.getKey());
				changed = true;
			}
		}
		if (changed || syncDelay-- <= 0) {
			syncDelay = SYNC_INTERVAL_TICKS;
			syncAll(world);
		}
	}

	private static void syncAll(ServerWorld world) {
		if (world == null) return;
		for (ServerPlayerEntity player : world.getPlayers()) syncTo(player);
	}

	private static void syncTo(ServerPlayerEntity player) {
		if (player == null || !ServerPlayNetworking.canSend(player, GuardianAngelShieldStatePayload.ID)) return;
		ServerPlayNetworking.send(player, DeadPlayerStatus.isDeadRoundParticipant(player)
			? statePayload(player.getServerWorld())
			: GuardianAngelShieldStatePayload.clear());
	}

	private static GuardianAngelShieldStatePayload statePayload(ServerWorld world) {
		if (world == null || shieldUntilByTarget.isEmpty()) return GuardianAngelShieldStatePayload.clear();
		long now = world.getTime();
		Map<UUID, Integer> remaining = new LinkedHashMap<>();
		for (Map.Entry<UUID, Long> entry : shieldUntilByTarget.entrySet()) {
			int ticks = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, entry.getValue() - now));
			if (ticks > 0) remaining.put(entry.getKey(), ticks);
		}
		return new GuardianAngelShieldStatePayload(remaining);
	}

	private static void clear() {
		guardianId = null;
		guardianUsed = false;
		shieldUntilByTarget.clear();
		syncDelay = 0;
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(guardianId, guardianUsed, Map.copyOf(shieldUntilByTarget));
	}

	public static void restoreForTimeRewind(ServerWorld world, TimeState state) {
		clear();
		if (state != null) {
			guardianId = state.guardianId();
			guardianUsed = state.guardianUsed();
			shieldUntilByTarget.putAll(state.shieldUntilByTarget());
		}
		syncAll(world);
	}

	public record TimeState(UUID guardianId, boolean guardianUsed, Map<UUID, Long> shieldUntilByTarget) {}
}
