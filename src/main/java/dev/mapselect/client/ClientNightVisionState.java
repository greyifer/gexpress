package dev.mapselect.client;

import dev.mapselect.network.NightVisionSyncPayload;
import dev.mapselect.registry.MapSelectModifiers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import org.agmas.harpymodloader.component.WorldModifierComponent;

public final class ClientNightVisionState {
	private static volatile boolean nightVision = false;

	private ClientNightVisionState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(NightVisionSyncPayload.ID, (payload, context) ->
			context.client().execute(() -> nightVision = payload.nightVision()));
	}

	public static boolean hasNightVision() {
		return nightVision || hasClientModifier();
	}

	private static boolean hasClientModifier() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.player == null) return false;
		try {
			WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(mc.world);
			return mods != null && mods.isModifier(mc.player, MapSelectModifiers.NIGHT_VISION);
		} catch (Throwable ignored) {
			return false;
		}
	}
}
