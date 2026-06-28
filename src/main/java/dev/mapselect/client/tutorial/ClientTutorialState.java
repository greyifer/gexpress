package dev.mapselect.client.tutorial;

import dev.mapselect.client.screen.GexpressTutorialStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public final class ClientTutorialState {
	private static boolean openedThisSession;
	private static int readyTicks;

	private ClientTutorialState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientTutorialState::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			openedThisSession = false;
			readyTicks = 0;
			return;
		}
		if (openedThisSession || !GexpressTutorialStore.shouldOpen()) return;
		if (client.currentScreen != null) {
			readyTicks = 0;
			return;
		}
		readyTicks++;
		if (readyTicks < 60) return;
		openedThisSession = true;
		ClientTutorialExperience.start();
	}
}
