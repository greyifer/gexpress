package dev.mapselect.client.hud;

import net.minecraft.client.MinecraftClient;

public final class ClientHudVisibility {
	private ClientHudVisibility() {}

	public static boolean shouldHide(MinecraftClient client) {
		return client == null || client.options == null || client.options.hudHidden;
	}
}
