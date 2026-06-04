package dev.mapselect.client;

import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.LoversStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.UUID;

public final class ClientLoversState {
	private static boolean active;
	private static UUID loverId;
	private static String loverName = "";

	private ClientLoversState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LoversStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		HudRenderCallback.EVENT.register(ClientLoversState::render);
	}

	private static void apply(LoversStatePayload payload) {
		active = payload.active();
		loverId = payload.loverId();
		loverName = payload.loverName();
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client == null || client.player == null
				|| !active || loverId == null || !GexpressConfig.shouldShowLoverHud()) {
			return;
		}
		String name = loverName == null || loverName.isBlank() ? "Unknown" : loverName;
		Text text = Text.literal("Lover: " + name);
		int x = 8;
		int y = Math.max(8, context.getScaledWindowHeight() - 42);
		context.drawTextWithShadow(client.textRenderer, text, x, y, 0xFFFF8FC1);
	}
}
