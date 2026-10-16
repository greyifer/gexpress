package dev.mapselect.role.silent;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.RoundLifecycleHooks;
import dev.mapselect.network.role.silent.ShadowMarchUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityCooldowns;
import dev.mapselect.role.AbilitySounds;
import dev.mapselect.role.ExternalRoleCompat;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.UUID;

public final class SilentShadowManager {
	private SilentShadowManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ShadowMarchUsePayload.ID, ShadowMarchUsePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShadowMarchUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryActivate(context.player())));
		AllowPlayerDeath.EVENT.register(SilentShadowManager::allowDeath);
		ServerTickEvents.END_WORLD_TICK.register(SilentShadowManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> resolveRejoinedShadow(server, handler.player)));
		RoundLifecycleHooks.clearWorld(SilentShadowManager::clear);
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (!(victim instanceof ServerPlayerEntity player)) return true;
		if (ExternalRoleCompat.isVoodooDeath(reason)) return true;
		SilentShadowComponent comp = SilentShadowComponent.KEY.getNullable(player.getWorld());
		if (comp == null || !comp.isActive(player.getUuid())) return true;
		player.sendMessage(Text.literal("Shadow March protected you."), true);
		return false;
	}

	private static void tryActivate(ServerPlayerEntity player) {
		if (player == null || player.getWorld().isClient) return;
		if (PelicanManager.isStashed(player)) return;
		if (!canUseSilentHere(player.getWorld(), player) || !isSilent(player)) return;
		if (!isPlayableForSilent(player)) return;

		SilentShadowComponent comp = SilentShadowComponent.KEY.getNullable(player.getWorld());
		if (comp == null) return;

		if (comp.isActive(player.getUuid())) {
			player.sendMessage(Text.literal("Shadow March is already active."), true);
			return;
		}

		boolean creativeBypass = GexpressTestState.hasCreativeAbilityBypass(player);
		long cooldown = creativeBypass ? 0L : comp.cooldownRemainingTicks(player.getUuid());
		if (!creativeBypass && cooldown > 0L) {
			player.sendMessage(Text.literal("Shadow March ready in " + Math.max(1L, AbilityCooldowns.secondsCeil(cooldown)) + "s."), true);
			return;
		}

		int durationTicks = GexpressConfig.getSilentShadowDurationSeconds() * 20;
		int cooldownTicks = creativeBypass ? 0 : GexpressConfig.getSilentShadowCooldownSeconds() * 20;
		if (!comp.activate(player, durationTicks, cooldownTicks)) {
			player.sendMessage(Text.literal("Shadow March failed."), true);
			return;
		}

		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,
			durationTicks + 5, 0, false, false, false));
		AbilitySounds.playTo(player, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.45F, 0.65F);
		player.sendMessage(Text.literal("Shadow March."), true);
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;

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
			if (state == null) {
				comp.remove(playerId);
				continue;
			}
			// Retain the persisted phase marker while offline. Rejoin handling resolves any
			// saved position that is still embedded in blocks before removing protection.
			if (player == null) continue;
			if (PelicanManager.isStashed(player) || !isPlayableForSilent(player)) {
				comp.remove(playerId);
				continue;
			}
			if (player.getWorld() != world || !canUseSilentHere(world, player) || !isSilent(player)) {
				comp.end(player, false);
				continue;
			}
			if (now >= state.activeUntil()) {
				if (comp.end(player, true)) {
					AbilitySounds.playTo(player, SoundEvents.ENTITY_ENDERMAN_TELEPORT,
						SoundCategory.PLAYERS, 0.55F, 0.85F);
					player.sendMessage(Text.literal("Returned from Shadow March."), true);
				}
			}
		}
	}

	private static void resolveRejoinedShadow(MinecraftServer server, ServerPlayerEntity player) {
		if (server == null || player == null) return;
		for (ServerWorld world : server.getWorlds()) {
			SilentShadowComponent comp = SilentShadowComponent.KEY.getNullable(world);
			if (comp == null || !comp.isActive(player.getUuid())) continue;
			if (PelicanManager.isStashed(player) || !isPlayableForSilent(player)) {
				comp.remove(player.getUuid());
			} else {
				comp.end(player, false);
			}
		}
	}

	private static void clear(World world) {
		SilentShadowComponent comp = SilentShadowComponent.KEY.getNullable(world);
		if (comp != null) comp.clearAll();
	}

	private static boolean isSilent(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && (MapSelectRoles.THE_SILENT_ID.equals(role.identifier())
			|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.THE_SILENT_ID));
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

}
