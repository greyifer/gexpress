package dev.mapselect.client;

import dev.mapselect.network.SeerDeathPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class ClientSeerState {
	private static int flashTicks;
	private static final int FLASH_DURATION = 26;

	private ClientSeerState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SeerDeathPayload.ID, (payload, context) ->
			context.client().execute(() -> flashTicks = FLASH_DURATION));
		HudRenderCallback.EVENT.register(ClientSeerState::renderHud);
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || flashTicks <= 0) return;
		float progress = flashTicks / (float) FLASH_DURATION;
		int alpha = Math.max(0, Math.min(145, Math.round(145.0F * progress)));
		int color = (alpha << 24) | 0xB00018;
		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), color);
		flashTicks--;
	}
}
