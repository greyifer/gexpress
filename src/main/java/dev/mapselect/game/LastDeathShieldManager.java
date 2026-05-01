package dev.mapselect.game;

import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class LastDeathShieldManager {
	private static final Set<UUID> lastRoundDeaths = new HashSet<>();
	private static final Set<UUID> currentRoundDeaths = new HashSet<>();
	private static final Set<UUID> shielded = new HashSet<>();

	private LastDeathShieldManager() {}

	public static void register() {
		AllowPlayerDeath.EVENT.register(LastDeathShieldManager::allowDeath);
		GameEvents.ON_FINISH_INITIALIZE.register(LastDeathShieldManager::onFinishInitialize);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> onFinishFinalize());
	}

	private static void onFinishInitialize(World world, GameWorldComponent game) {
		currentRoundDeaths.clear();
		shielded.clear();
		if (!(world instanceof ServerWorld serverWorld) || !GexpressConfig.isLastDeathShieldEnabled()) return;
		for (ServerPlayerEntity player : serverWorld.getPlayers()) {
			if (lastRoundDeaths.contains(player.getUuid()) && GameFunctions.isPlayerAliveAndSurvival(player)) {
				shielded.add(player.getUuid());
				player.sendMessage(Text.literal("Last-round shield active.").formatted(Formatting.AQUA), true);
			}
		}
	}

	private static void onFinishFinalize() {
		lastRoundDeaths.clear();
		lastRoundDeaths.addAll(currentRoundDeaths);
		currentRoundDeaths.clear();
		shielded.clear();
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (!(victim instanceof ServerPlayerEntity player)) return true;
		if (shielded.contains(player.getUuid()) && isShieldBreakingHit(reason)) {
			shielded.remove(player.getUuid());
			player.sendMessage(Text.literal("Your shield broke."), true);
			if (killer instanceof ServerPlayerEntity attacker) {
				attacker.sendMessage(Text.literal(player.getName().getString() + "'s shield broke."), true);
			}
			return false;
		}

		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
			currentRoundDeaths.add(player.getUuid());
		}
		return true;
	}

	private static boolean isShieldBreakingHit(Identifier reason) {
		return GameConstants.DeathReasons.KNIFE.equals(reason) || GameConstants.DeathReasons.GUN.equals(reason);
	}
}
