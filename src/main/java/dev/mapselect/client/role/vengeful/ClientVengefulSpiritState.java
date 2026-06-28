package dev.mapselect.client.role.vengeful;
import dev.mapselect.client.hud.ClientHudVisibility;


import dev.mapselect.network.role.vengeful.VengefulSpiritStatePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.UUID;

public final class ClientVengefulSpiritState {
	public static final int KILLER_GLOW_COLOR = 0x55E6FF;
	private static int phase;
	private static UUID killerId;
	private static String killerName = "";
	private static long remainingTicks;
	private static long totalTicks;

	private ClientVengefulSpiritState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(VengefulSpiritStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (phase != VengefulSpiritStatePayload.NONE && remainingTicks > 0L) remainingTicks--;
		});
		HudRenderCallback.EVENT.register(ClientVengefulSpiritState::renderHud);
	}

	private static void apply(VengefulSpiritStatePayload payload) {
		phase = payload.phase();
		killerId = payload.killerId();
		killerName = payload.killerName();
		remainingTicks = payload.remainingTicks();
		totalTicks = payload.totalTicks();
	}

	public static boolean shouldGlow(AbstractClientPlayerEntity player) {
		return phase == VengefulSpiritStatePayload.ACTIVE && player != null && killerId != null
			&& killerId.equals(player.getUuid());
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (phase == VengefulSpiritStatePayload.NONE || ClientHudVisibility.shouldHide(client)
				|| client == null || client.player == null || client.textRenderer == null) return;
		long seconds = Math.max(0L, (remainingTicks + 19L) / 20L);
		String label = phase == VengefulSpiritStatePayload.PENDING
			? "Returning in " + seconds + "s"
			: "Vengeance: " + seconds + "s";
		if (!killerName.isBlank()) label += " - " + killerName;
		Text text = Text.literal(label);
		int x = (context.getScaledWindowWidth() - client.textRenderer.getWidth(text)) / 2;
		int right = x + client.textRenderer.getWidth(text) + 7;
		context.fill(x - 7, 7, right, 24, 0xB0121B22);
		context.drawTextWithShadow(client.textRenderer, text, x, 10, 0xFF75E9FF);
		if (totalTicks > 0L) {
			int barWidth = Math.max(1, right - (x - 7));
			int filled = Math.max(0, Math.min(barWidth,
				(int) Math.round(barWidth * (remainingTicks / (double) totalTicks))));
			context.fill(x - 7, 22, right, 24, 0xFF263944);
			context.fill(x - 7, 22, x - 7 + filled, 24, 0xFF55E6FF);
		}
	}
}
