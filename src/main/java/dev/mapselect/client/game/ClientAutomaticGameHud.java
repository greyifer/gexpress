package dev.mapselect.client.game;

import dev.mapselect.network.game.AutoGameStatusPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ClientAutomaticGameHud {
	private static AutoGameStatusPayload current = new AutoGameStatusPayload(false, -1, 0, 6);

	private ClientAutomaticGameHud() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AutoGameStatusPayload.ID, (payload, context) ->
			context.client().execute(() -> current = payload));
		HudRenderCallback.EVENT.register(ClientAutomaticGameHud::renderHud);
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.currentScreen != null) return;
		if (!current.active() || current.countdownSeconds() < 0) return;

		Text text = Text.literal("Automatic game starts in " + current.countdownSeconds() + "s")
			.formatted(Formatting.GREEN);
		int x = (context.getScaledWindowWidth() - client.textRenderer.getWidth(text)) / 2;
		context.drawTextWithShadow(client.textRenderer, text, x, 18, 0xFF66FF8A);
	}
}
