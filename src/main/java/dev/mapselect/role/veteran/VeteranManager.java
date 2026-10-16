package dev.mapselect.role.veteran;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.RoundLifecycleHooks;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.veteran.VeteranUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilitySounds;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VeteranManager {
	public static final int ALERT_DURATION_TICKS = 15 * 20;
	private static final Map<UUID, Long> ALERT_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> USES_SPENT = new ConcurrentHashMap<>();
	private static int syncTicker;

	private VeteranManager() {}

	public static void register() {
		AllowPlayerDeath.EVENT.register(VeteranManager::allowDeath);
		PayloadTypeRegistry.playC2S().register(VeteranUsePayload.ID, VeteranUsePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(VeteranUsePayload.ID,
			(payload, context) -> context.server().execute(() -> activate(context.player())));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> sync(handler.player)));
		ServerTickEvents.END_WORLD_TICK.register(VeteranManager::tick);
		RoundLifecycleHooks.clearWorld(VeteranManager::clear);
	}

	private static void activate(ServerPlayerEntity veteran) {
		if (!canUse(veteran)) return;
		long now = veteran.getWorld().getTime();
		long activeUntil = ALERT_UNTIL.getOrDefault(veteran.getUuid(), 0L);
		if (activeUntil > now) {
			sync(veteran);
			veteran.sendMessage(Text.literal("Veteran Alert is already active.")
				.formatted(Formatting.GRAY), true);
			return;
		}

		if (usesRemaining(veteran) <= 0 && !GexpressTestState.hasCreativeAbilityBypass(veteran)) {
			sync(veteran);
			veteran.sendMessage(Text.literal("No Veteran Alerts left.").formatted(Formatting.RED), true);
			return;
		}

		ALERT_UNTIL.put(veteran.getUuid(), now + ALERT_DURATION_TICKS);
		sync(veteran);
		AbilitySounds.playTo(veteran, SoundEvents.BLOCK_IRON_DOOR_CLOSE,
			SoundCategory.PLAYERS, 0.75F, 0.8F);
		veteran.sendMessage(Text.literal("Veteran Alert active for 15 seconds.")
			.formatted(Formatting.GOLD), true);
	}

	/**
	 * Resolves a role-ability interaction before it changes the Veteran. A true result means Alert
	 * cancelled the interaction and the caller must stop applying the ability.
	 */
	public static boolean interceptAbilityInteraction(ServerPlayerEntity visitor, ServerPlayerEntity veteran) {
		if (visitor == null || veteran == null || visitor == veteran
				|| visitor.getWorld() != veteran.getWorld() || !isAlertActive(veteran)
				|| !isPlayable(visitor, veteran) || !isPlayable(veteran, veteran)) {
			return false;
		}

		triggerAlert(veteran, visitor);
		return true;
	}

	public static boolean isAlertActive(PlayerEntity player) {
		if (player == null || !isVeteran(player)) return false;
		return ALERT_UNTIL.getOrDefault(player.getUuid(), 0L) > player.getWorld().getTime();
	}

	/** Used by optional Guesser integrations that only expose the guessed player's UUID. */
	public static boolean isAlertActive(UUID playerId) {
		return playerId != null && ALERT_UNTIL.containsKey(playerId);
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer,
			net.minecraft.util.Identifier reason) {
		if (!(victim instanceof ServerPlayerEntity veteran) || !isAlertActive(veteran)) {
			return true;
		}
		if (!GameConstants.DeathReasons.KNIFE.equals(reason)
				&& !GameConstants.DeathReasons.GUN.equals(reason)
				&& !GameConstants.DeathReasons.GRENADE.equals(reason)) {
			return true;
		}
		triggerAlert(veteran, killer instanceof ServerPlayerEntity attacker ? attacker : null);
		return false;
	}

	private static void triggerAlert(ServerPlayerEntity veteran, ServerPlayerEntity source) {
		finishAlert(veteran, true);
		List<ServerPlayerEntity> listeners = source == null || source == veteran
			? List.of(veteran) : List.of(source, veteran);
		AbilitySounds.playTo(listeners, SoundEvents.ITEM_SHIELD_BLOCK,
			SoundCategory.PLAYERS, 0.8F, 0.85F);
		String sourceName = source == null ? "a lethal attack" : source.getName().getString();
		veteran.sendMessage(Text.literal("Alert stopped " + sourceName + ".")
			.formatted(Formatting.GOLD), true);
	}

	private static void finishAlert(ServerPlayerEntity veteran, boolean triggered) {
		if (veteran == null || ALERT_UNTIL.remove(veteran.getUuid()) == null) return;
		if (!GexpressTestState.hasCreativeAbilityBypass(veteran)) {
			USES_SPENT.merge(veteran.getUuid(), 1, Integer::sum);
		}
		sync(veteran);
		if (!triggered) {
			veteran.sendMessage(Text.literal("Veteran Alert expired.").formatted(Formatting.GRAY), true);
		}
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if ((game == null || !game.isRunning()) && !GexpressTestState.hasRoleTesters()) {
			clear(world);
			return;
		}

		long now = world.getTime();
		for (UUID veteranId : Set.copyOf(ALERT_UNTIL.keySet())) {
			ServerPlayerEntity veteran = world.getServer().getPlayerManager().getPlayer(veteranId);
			if (veteran == null || veteran.getWorld() != world || !isVeteran(veteran)
					|| PelicanManager.isStashed(veteran) || !isPlayable(veteran, veteran)) {
				ALERT_UNTIL.remove(veteranId);
				continue;
			}
			if (ALERT_UNTIL.getOrDefault(veteranId, 0L) <= now) finishAlert(veteran, false);
		}

		if (syncTicker-- <= 0) {
			syncTicker = 10;
			for (ServerPlayerEntity player : world.getPlayers(VeteranManager::isVeteran)) sync(player);
		}
	}

	private static void sync(ServerPlayerEntity veteran) {
		if (veteran == null || !isVeteran(veteran)) return;
		long remaining = Math.max(0L,
			ALERT_UNTIL.getOrDefault(veteran.getUuid(), 0L) - veteran.getWorld().getTime());
		AbilityCooldownSync.sendUses(veteran, AbilityCooldownPayload.VETERAN_ALERT,
			remaining, ALERT_DURATION_TICKS, usesRemaining(veteran), maxUses());
	}

	private static int usesRemaining(ServerPlayerEntity veteran) {
		if (GexpressTestState.hasCreativeAbilityBypass(veteran)) return maxUses();
		return Math.max(0, maxUses() - USES_SPENT.getOrDefault(veteran.getUuid(), 0));
	}

	private static int maxUses() {
		return GexpressConfig.getVeteranMaxUses();
	}

	private static boolean canUse(ServerPlayerEntity veteran) {
		if (veteran == null || !(veteran.getWorld() instanceof ServerWorld world)
				|| PelicanManager.isStashed(veteran) || !isVeteran(veteran)
				|| !isPlayable(veteran, veteran)) {
			return false;
		}
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(veteran);
	}

	private static boolean isVeteran(PlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && (MapSelectRoles.VETERAN_ID.equals(role.identifier())
			|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.VETERAN_ID));
	}

	private static boolean isPlayable(PlayerEntity player, PlayerEntity user) {
		if (GexpressTestState.isRoleTester(user)) return player != null && !player.isSpectator();
		if (player instanceof ServerPlayerEntity serverPlayer) {
			return DeadPlayerStatus.isLivingRoundParticipant(serverPlayer);
		}
		return GameFunctions.isPlayerAliveAndSurvival(player);
	}

	private static void clear(World world) {
		ALERT_UNTIL.clear();
		USES_SPENT.clear();
		syncTicker = 0;
	}
}
