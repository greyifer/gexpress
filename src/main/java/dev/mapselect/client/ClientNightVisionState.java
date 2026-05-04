package dev.mapselect.client;

import dev.mapselect.network.NightVisionSyncPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.MathHelper;

public final class ClientNightVisionState {
	private static volatile boolean nightVision = false;
	private static float strength = 0.0F;

	private ClientNightVisionState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(NightVisionSyncPayload.ID, (payload, context) ->
			context.client().execute(() -> nightVision = payload.nightVision()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client == null || client.world == null || client.player == null) {
				nightVision = false;
			}
			strength = MathHelper.lerp(0.18F, strength, nightVision ? 1.0F : 0.0F);
		});
	}

	public static boolean hasNightVision() {
		return strength > 0.01F;
	}

	public static boolean isEnabled() {
		return nightVision;
	}

	public static float strength() {
		return strength;
	}
}
