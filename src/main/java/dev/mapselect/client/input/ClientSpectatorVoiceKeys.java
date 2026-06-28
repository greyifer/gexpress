package dev.mapselect.client.input;

import dev.mapselect.network.voice.SpectatorVoiceGroupPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class ClientSpectatorVoiceKeys {
	private static boolean wasPreviousDown;
	private static boolean wasNextDown;

	private ClientSpectatorVoiceKeys() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientSpectatorVoiceKeys::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.currentScreen != null
				|| !ClientPlayNetworking.canSend(SpectatorVoiceGroupPayload.ID)) {
			wasPreviousDown = false;
			wasNextDown = false;
			return;
		}
		boolean previous = isDown(client, ClientAbilityKeys.spectatorVoicePreviousBinding());
		boolean next = isDown(client, ClientAbilityKeys.spectatorVoiceNextBinding());
		if (previous && !wasPreviousDown) {
			ClientPlayNetworking.send(new SpectatorVoiceGroupPayload(-1));
		} else if (next && !wasNextDown) {
			ClientPlayNetworking.send(new SpectatorVoiceGroupPayload(1));
		}
		wasPreviousDown = previous;
		wasNextDown = next;
	}

	private static boolean isDown(MinecraftClient client, KeyBinding binding) {
		return binding != null && ClientAbilityKeys.isDown(client, binding);
	}
}
