package dev.mapselect.client;

import cat.rezelyn.watheextended.client.screen.GuidebookScreen;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class ClientDeadGuidebookKey {
	private static boolean wasGuidebookDown;

	private ClientDeadGuidebookKey() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientDeadGuidebookKey::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			wasGuidebookDown = false;
			return;
		}

		KeyBinding binding = ClientAbilityKeys.guidebookBinding();
		boolean down = binding != null && ClientAbilityKeys.isDown(client, binding);
		if (down && !wasGuidebookDown && client.currentScreen == null && isLocalDeadParticipant(client)) {
			client.setScreen(new GuidebookScreen());
		}
		wasGuidebookDown = down;
	}

	private static boolean isLocalDeadParticipant(MinecraftClient client) {
		if (ClientVultureState.isLocalStashed(client) || !client.player.isSpectator()) return false;
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			return game != null && game.getRoles().containsKey(client.player.getUuid());
		} catch (Throwable ignored) {
			return false;
		}
	}
}
