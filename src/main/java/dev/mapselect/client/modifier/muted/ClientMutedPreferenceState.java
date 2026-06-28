package dev.mapselect.client.modifier.muted;

import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.modifier.MutedPreferencePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientMutedPreferenceState {
	private ClientMutedPreferenceState() {}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(
			ClientMutedPreferenceState::send));
	}

	public static void setEnabled(boolean enabled) {
		GexpressConfig.alwaysMuted = enabled;
		GexpressConfig.save();
		send();
	}

	private static void send() {
		if (ClientPlayNetworking.canSend(MutedPreferencePayload.ID)) {
			ClientPlayNetworking.send(new MutedPreferencePayload(GexpressConfig.alwaysMuted()));
		}
	}
}
