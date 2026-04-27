package dev.mapselect.role.seer;

import dev.doctor4t.wathe.api.Role;
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
import net.minecraft.world.World;

public final class SeerManager {
	private SeerManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SeerDeathPayload.ID, SeerDeathPayload.CODEC);
		ServerLivingEntityEvents.AFTER_DEATH.register(SeerManager::afterDeath);
	}

	private static void afterDeath(LivingEntity entity, net.minecraft.entity.damage.DamageSource source) {
		if (!(entity instanceof ServerPlayerEntity)) return;
		if (!(entity.getWorld() instanceof ServerWorld world) || world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
				&& !GexpressTestState.hasRoleTesters())) {
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player == entity || !isSeer(game, player) || !isPlayable(player)) continue;
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
