package dev.mapselect.client;

import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.mixin.client.RoundTextRendererAccessor;
import net.minecraft.client.MinecraftClient;

public final class ClientRoleRevealState {
	private static final int ROLE_TEXT_VISIBLE_TICK = 180;

	private ClientRoleRevealState() {}

	public static boolean canShowRoleHud(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) return false;
		if (!GameFunctions.isPlayerAliveAndSurvival(client.player)) return false;
		return true;
	}

	public static boolean isRoleRevealSettled() {
		return welcomeTime() <= ROLE_TEXT_VISIBLE_TICK;
	}

	public static boolean canUseRoleAbility(MinecraftClient client) {
		return canShowRoleHud(client);
	}

	private static int welcomeTime() {
		try {
			return RoundTextRendererAccessor.gexpress$getWelcomeTime();
		} catch (Throwable ignored) {
			return 0;
		}
	}
}
