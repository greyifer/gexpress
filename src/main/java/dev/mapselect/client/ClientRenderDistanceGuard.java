package dev.mapselect.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public final class ClientRenderDistanceGuard {
	private static final int TARGET_VIEW_DISTANCE = 32;
	private static int ticksUntilCheck;

	private ClientRenderDistanceGuard() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientRenderDistanceGuard::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.options == null || client.world == null) return;
		if (ticksUntilCheck-- > 0) return;
		ticksUntilCheck = 20;
		if (client.options.getViewDistance().getValue() < TARGET_VIEW_DISTANCE) {
			client.options.getViewDistance().setValue(TARGET_VIEW_DISTANCE);
		}
	}
}
