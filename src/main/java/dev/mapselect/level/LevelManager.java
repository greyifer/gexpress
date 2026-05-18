package dev.mapselect.level;

import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.command.admin.TagCommand;
import dev.mapselect.config.GexpressConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LevelManager {
	private static final int KILL_XP_DEDUP_TICKS = 20;
	private static final Map<UUID, RecentKillAward> recentKillXpAwards = new HashMap<>();

	private LevelManager() {}

	public static void register() {
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
			recentKillXpAwards.clear();
			grantRoundXp(world, game);
		});
		AllowPlayerDeath.EVENT.register(LevelManager::grantKillXpFromAllowedDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(LevelManager::grantKillXpAfterDeath);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> {
				if (handler.player.getWorld() instanceof ServerWorld world) {
					LevelComponent.KEY.sync(world);
					TagCommand.refreshPlayerListName(handler.player);
				}
			}));
	}

	public static void grantCivilianTaskXp(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || !game.isRunning() || !isCivilianSide(game, player)) return;
		LevelComponent levels = LevelComponent.KEY.get(world);
		if (levels.addXp(player.getUuid(), GexpressConfig.getLevelCivilianTaskXp())) {
			TagCommand.refreshPlayerListName(player);
		}
	}

	private static void grantRoundXp(World rawWorld, GameWorldComponent game) {
		if (!(rawWorld instanceof ServerWorld world)) return;
		GameRoundEndComponent roundEnd = GameRoundEndComponent.KEY.getNullable(world);
		if (roundEnd == null || roundEnd.getWinStatus() == GameFunctions.WinStatus.NONE) return;
		LevelComponent levels = LevelComponent.KEY.get(world);
		Set<UUID> awarded = new LinkedHashSet<>();
		for (GameRoundEndComponent.RoundEndData entry : roundEnd.getPlayers()) {
			if (entry == null || entry.player() == null || !awarded.add(entry.player().getId())) continue;
			boolean won = roundEnd.didWin(entry.player().getId());
			int amount = GexpressConfig.getLevelRoundXp() + (won ? GexpressConfig.getLevelWinXp() : 0);
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.player().getId());
			if (won && player != null && isNeutralSide(game, player)) {
				amount += GexpressConfig.getLevelNeutralWinBonusXp();
			}
			levels.addXp(entry.player().getId(), amount);
		}
		if (!awarded.isEmpty()) {
			LevelComponent.KEY.sync(world);
			for (ServerPlayerEntity player : world.getPlayers()) {
				TagCommand.refreshPlayerListName(player);
			}
		}
	}

	private static boolean grantKillXpFromAllowedDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		awardKillXp(victim, killer);
		return true;
	}

	private static void grantKillXpAfterDeath(net.minecraft.entity.LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayerEntity victim) || source == null) return;
		if (!(source.getAttacker() instanceof ServerPlayerEntity killer) || killer == victim) return;
		awardKillXp(victim, killer);
	}

	private static void awardKillXp(PlayerEntity victim, PlayerEntity killer) {
		if (!(victim instanceof ServerPlayerEntity serverVictim)
				|| !(killer instanceof ServerPlayerEntity serverKiller)
				|| serverKiller == serverVictim) {
			return;
		}
		if (GexpressConfig.getLevelKillXp() <= 0) return;
		if (!(serverKiller.getWorld() instanceof ServerWorld world) || serverVictim.getWorld() != world) return;
		if (!GameFunctions.isPlayerAliveAndSurvival(serverKiller)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || !game.isRunning()) return;
		if (!game.getRoles().containsKey(serverVictim.getUuid())
				|| !game.getRoles().containsKey(serverKiller.getUuid())) return;
		if (wasRecentlyAwarded(serverVictim.getUuid(), serverKiller.getUuid(), world.getTime())) return;
		LevelComponent levels = LevelComponent.KEY.get(world);
		if (levels.addXp(serverKiller.getUuid(), GexpressConfig.getLevelKillXp())) {
			TagCommand.refreshPlayerListName(serverKiller);
		}
	}

	private static boolean wasRecentlyAwarded(UUID victimId, UUID killerId, long tick) {
		recentKillXpAwards.entrySet().removeIf(entry -> tick - entry.getValue().tick() > KILL_XP_DEDUP_TICKS);
		RecentKillAward previous = recentKillXpAwards.get(victimId);
		if (previous != null && previous.killerId().equals(killerId)
				&& tick - previous.tick() <= KILL_XP_DEDUP_TICKS) {
			return true;
		}
		recentKillXpAwards.put(victimId, new RecentKillAward(killerId, tick));
		return false;
	}

	private static boolean isCivilianSide(GameWorldComponent game, PlayerEntity player) {
		Role role = game == null || player == null ? null : game.getRole(player);
		return role != null && role.isInnocent() && !game.canUseKillerFeatures(player);
	}

	private static boolean isNeutralSide(GameWorldComponent game, PlayerEntity player) {
		Role role = game == null || player == null ? null : game.getRole(player);
		return role != null && !role.canUseKiller() && !role.isInnocent();
	}

	private record RecentKillAward(UUID killerId, long tick) {}
}
