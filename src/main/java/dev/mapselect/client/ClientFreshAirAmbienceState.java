package dev.mapselect.client;

import dev.mapselect.network.FreshAirAmbienceStatePayload;
import dev.mapselect.task.FreshAirAreaManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public final class ClientFreshAirAmbienceState {
	private ClientFreshAirAmbienceState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FreshAirAmbienceStatePayload.ID, (payload, context) ->
			context.client().execute(() ->
				FreshAirAreaManager.setClientLocalFreshAirAmbience(payload.playOutsideAmbience())));
		ClientTickEvents.END_CLIENT_TICK.register(ClientFreshAirAmbienceState::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			FreshAirAreaManager.setClientLocalFreshAirAmbience(false);
		}
	}
}
