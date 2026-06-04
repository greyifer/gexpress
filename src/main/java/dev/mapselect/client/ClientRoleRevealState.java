package dev.mapselect.client;

import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.mixin.client.RoundTextRendererAccessor;
import net.minecraft.client.MinecraftClient;

public final class ClientRoleRevealState {
	private static final int ROLE_TEXT_VISIBLE_TICK = 180;
	private static final int SAFE_PREPARATION_TICKS = 30 * 20;

	private ClientRoleRevealState() {}

	public static boolean canShowRoleHud(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) return false;
		if (isCreativeRolePreview(client)) return true;
		if (!GameFunctions.isPlayerAliveAndSurvival(client.player)) return false;
		return true;
	}

	public static boolean isRoleRevealSettled() {
		return welcomeTime() <= ROLE_TEXT_VISIBLE_TICK;
	}

	public static boolean canUseRoleAbility(MinecraftClient client) {
		if (!canShowRoleHud(client)) return false;
		return isCreativeRolePreview(client) || !isSafePreparation(client);
	}

	public static boolean isCreativeRolePreview(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null || !client.player.isCreative()) return false;
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			return game != null && game.getRole(client.player) != null;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean isSafePreparation(MinecraftClient client) {
		if (client == null || client.world == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return false;
		GameTimeComponent time = GameTimeComponent.KEY.getNullable(client.world);
		if (time == null || time.resetTime <= 0 || time.getTime() <= 0) return false;
		int elapsed = time.resetTime - time.getTime();
		return elapsed >= 0 && elapsed < SAFE_PREPARATION_TICKS;
	}

	private static int welcomeTime() {
		try {
			return RoundTextRendererAccessor.gexpress$getWelcomeTime();
		} catch (Throwable ignored) {
			return 0;
		}
	}
}
