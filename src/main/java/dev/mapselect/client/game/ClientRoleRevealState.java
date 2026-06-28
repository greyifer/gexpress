package dev.mapselect.client.game;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.game.KinsWatheSafePreparation;
import dev.mapselect.mixin.client.RoundTextRendererAccessor;
import net.minecraft.client.MinecraftClient;

public final class ClientRoleRevealState {
	private static final int ROLE_TEXT_VISIBLE_TICK = 180;

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
		return client != null && KinsWatheSafePreparation.isActive(client.world);
	}

	private static int welcomeTime() {
		try {
			return RoundTextRendererAccessor.gexpress$getWelcomeTime();
		} catch (Throwable ignored) {
			return 0;
		}
	}
}
