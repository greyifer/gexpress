package dev.mapselect.role.seer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.network.SeerDeathPayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SeerManager {
	private static final Map<UUID, Long> RECENT_DEATH_FLASHES = new ConcurrentHashMap<>();

	private SeerManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SeerDeathPayload.ID, SeerDeathPayload.CODEC);
		AllowPlayerDeath.EVENT.register(SeerManager::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(SeerManager::afterDeath);
	}

	private static boolean allowDeath(net.minecraft.entity.player.PlayerEntity victim,
			net.minecraft.entity.player.PlayerEntity killer, Identifier reason) {
		if (victim instanceof ServerPlayerEntity player) notifySeers(player);
		return true;
	}

	private static void afterDeath(LivingEntity entity, net.minecraft.entity.damage.DamageSource source) {
		if (entity instanceof ServerPlayerEntity player) notifySeers(player);
	}

	private static void notifySeers(ServerPlayerEntity deadPlayer) {
		if (!(deadPlayer.getWorld() instanceof ServerWorld world) || world.getRegistryKey() != World.OVERWORLD) return;
		long now = world.getTime();
		Long last = RECENT_DEATH_FLASHES.put(deadPlayer.getUuid(), now);
		if (last != null && now - last <= 5L) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
				&& !GexpressTestState.hasRoleTesters())) {
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player == deadPlayer || !isSeer(game, player) || !isPlayable(player)) continue;
			if (ServerPlayNetworking.canSend(player, SeerDeathPayload.ID)) {
				ServerPlayNetworking.send(player, new SeerDeathPayload());
			}
		}
	}

	private static boolean isSeer(GameWorldComponent game, ServerPlayerEntity player) {
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.SEER_ID.equals(role.identifier());
	}

	private static boolean isPlayable(ServerPlayerEntity player) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(player);
	}
}
