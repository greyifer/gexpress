package dev.mapselect.client;

import dev.mapselect.game.GexpressAbilityGuards;
import dev.mapselect.mixin.client.RoundTextRendererAccessor;
import net.minecraft.client.MinecraftClient;

public final class ClientRoleRevealState {
	private static final int ROLE_TEXT_VISIBLE_TICK = 180;

	private ClientRoleRevealState() {}

	public static boolean canShowRoleHud(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) return false;
		return welcomeTime() <= ROLE_TEXT_VISIBLE_TICK;
	}

	public static boolean canUseRoleAbility(MinecraftClient client) {
		return canShowRoleHud(client) && !GexpressAbilityGuards.isSafePreparation(client.world);
	}

	private static int welcomeTime() {
		try {
			return RoundTextRendererAccessor.gexpress$getWelcomeTime();
		} catch (Throwable ignored) {
			return 0;
		}
	}
}
