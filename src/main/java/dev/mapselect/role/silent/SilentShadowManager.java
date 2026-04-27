package dev.mapselect.role.silent;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.ShadowMarchUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.UUID;

public final class SilentShadowManager {
	private SilentShadowManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ShadowMarchUsePayload.ID, ShadowMarchUsePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShadowMarchUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryActivate(context.player())));
		ServerTickEvents.END_WORLD_TICK.register(SilentShadowManager::tick);
	}

	private static void tryActivate(ServerPlayerEntity player) {
		if (player == null || player.getWorld().isClient) return;
		if (!canUseSilentHere(player.getWorld(), player) || !isSilent(player)) return;
		if (!isPlayableForSilent(player)) return;

		SilentShadowComponent comp = SilentShadowComponent.KEY.getNullable(player.getWorld());
		if (comp == null) return;

		if (comp.isActive(player.getUuid())) {
			player.sendMessage(Text.literal("Shadow March is already active."), true);
			return;
		}

		long cooldown = comp.cooldownRemainingTicks(player.getUuid());
		if (cooldown > 0L) {
			player.sendMessage(Text.literal("Shadow March ready in " + secondsCeil(cooldown) + "s."), true);
			return;
		}

		int durationTicks = GexpressConfig.getSilentShadowDurationSeconds() * 20;
		int cooldownTicks = GexpressConfig.getSilentShadowCooldownSeconds() * 20;
		if (!comp.activate(player, durationTicks, cooldownTicks)) {
			player.sendMessage(Text.literal("Shadow March failed."), true);
			return;
		}

		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,
			durationTicks + 5, 0, false, false, false));
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.45F, 0.65F);
		player.sendMessage(Text.literal("Shadow March."), true);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;

		SilentShadowComponent comp = SilentShadowComponent.KEY.getNullable(world);
		if (comp == null) return;

		if (!isActiveGame(world) && !GexpressTestState.hasRoleTesters()) {
			comp.clearAll();
			return;
		}

		MinecraftServer server = world.getServer();
		long now = world.getTime();
		for (UUID playerId : comp.getActivePlayerIds()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
			SilentShadowComponent.ShadowState state = comp.getState(playerId);
			if (player == null || state == null) {
				comp.remove(playerId);
				continue;
			}
			if (!canUseSilentHere(world, player) || !isPlayableForSilent(player) || !isSilent(player)) {
				comp.end(player, false);
				continue;
			}
			if (now >= state.activeUntil()) {
				comp.end(player, true);
				player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.55F, 0.85F);
				player.sendMessage(Text.literal("Returned from Shadow March."), true);
			}
		}
	}

	private static boolean isSilent(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.THE_SILENT_ID.equals(role.identifier());
	}

	private static boolean isActiveGame(World world) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
	}

	private static boolean canUseSilentHere(World world, PlayerEntity player) {
		return isActiveGame(world) || GexpressTestState.isRoleTester(player);
	}

	private static boolean isPlayableForSilent(PlayerEntity player) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(player);
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}
}
