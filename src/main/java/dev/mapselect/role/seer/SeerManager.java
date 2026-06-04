package dev.mapselect.role.seer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.network.AbilityCooldownSync;
import dev.mapselect.network.SeerDeathPayload;
import dev.mapselect.network.SeerCompareUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.RoleTeams;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SeerManager {
	private static final Map<UUID, Long> RECENT_DEATH_FLASHES = new ConcurrentHashMap<>();
	private static final Map<UUID, UUID> FIRST_TARGETS = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

	private SeerManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SeerDeathPayload.ID, SeerDeathPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SeerCompareUsePayload.ID, SeerCompareUsePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SeerCompareUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryCompare(context.player())));
		AllowPlayerDeath.EVENT.register(SeerManager::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(SeerManager::afterDeath);
	}

	public static void reduceCooldown(ServerPlayerEntity player, long ticks) {
		if (player == null || ticks <= 0L) return;
		UUID id = player.getUuid();
		Long until = COOLDOWN_UNTIL.get(id);
		if (until == null) return;
		COOLDOWN_UNTIL.put(id, Math.max(0L, until - ticks));
	}

	private static void tryCompare(ServerPlayerEntity seer) {
		if (seer == null || !(seer.getWorld() instanceof ServerWorld world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || !isSeer(game, seer) || PelicanManager.isStashed(seer)
				|| !canUseHere(world, seer) || !isPlayable(seer)) {
			return;
		}

		long now = world.getTime();
		long remaining = COOLDOWN_UNTIL.getOrDefault(seer.getUuid(), 0L) - now;
		if (remaining > 0L && !GexpressTestState.hasCreativeAbilityBypass(seer)) {
			AbilityCooldownSync.send(seer, AbilityCooldownPayload.SEER_COMPARE, remaining,
				GexpressConfig.getSeerCompareCooldownSeconds() * 20L, false);
			return;
		}

		ServerPlayerEntity target = AbilityTargeting.findLookTarget(seer, world.getPlayers(),
			GexpressConfig.getSeerCompareRange(), 0.25D, true, candidate ->
				candidate != seer && !PelicanManager.isStashed(candidate) && isPlayable(candidate));
		if (target == null) {
			seer.sendMessage(Text.literal("No living player close enough to read.").formatted(Formatting.RED), true);
			return;
		}

		UUID firstId = FIRST_TARGETS.get(seer.getUuid());
		if (firstId == null) {
			FIRST_TARGETS.put(seer.getUuid(), target.getUuid());
			seer.sendMessage(Text.literal("First soul read: " + target.getName().getString() + ".")
				.formatted(Formatting.LIGHT_PURPLE), true);
			return;
		}
		if (firstId.equals(target.getUuid())) {
			seer.sendMessage(Text.literal("Pick a different second player.").formatted(Formatting.RED), true);
			return;
		}

		ServerPlayerEntity first = world.getServer().getPlayerManager().getPlayer(firstId);
		FIRST_TARGETS.remove(seer.getUuid());
		if (first == null || !isPlayable(first)) {
			seer.sendMessage(Text.literal("Your first target is no longer readable.").formatted(Formatting.RED), true);
			return;
		}

		boolean same = RoleTeams.sameTeam(game, first, target);
		seer.sendMessage(Text.literal(first.getName().getString() + " and " + target.getName().getString()
			+ (same ? " are on the same team." : " are not on the same team."))
			.formatted(same ? Formatting.GREEN : Formatting.RED), true);
		if (!GexpressTestState.hasCreativeAbilityBypass(seer)) {
			long total = GexpressConfig.getSeerCompareCooldownSeconds() * 20L;
			COOLDOWN_UNTIL.put(seer.getUuid(), now + total);
			AbilityCooldownSync.send(seer, AbilityCooldownPayload.SEER_COMPARE, total, total, false);
		}
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
		return (GameFunctions.isPlayerAliveAndSurvival(player) && DeadPlayerStatus.isLivingRoundParticipant(player))
			|| GexpressTestState.isRoleTester(player);
	}

	private static boolean canUseHere(World world, ServerPlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}
}
