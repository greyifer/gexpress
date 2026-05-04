package dev.mapselect.client;

import dev.mapselect.network.JuggernautStagePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public final class ClientJuggernautState {
	private static boolean active;
	private static int stage;
	private static int maxStage = 5;
	private static int reductionSeconds;
	private static boolean knifeShield;
	private static boolean gunShield;
	private static float alpha;
	private static float slide;
	private static float pulse;

	private ClientJuggernautState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(JuggernautStagePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientJuggernautState::tick);
		HudRenderCallback.EVENT.register(ClientJuggernautState::render);
	}

	private static void apply(JuggernautStagePayload payload) {
		if (stage != payload.stage()) pulse = 1.0F;
		active = payload.active();
		stage = payload.stage();
		maxStage = payload.maxStage();
		reductionSeconds = payload.cooldownReductionSeconds();
		knifeShield = payload.knifeShield();
		gunShield = payload.gunShield();
	}

	private static void tick(MinecraftClient client) {
		boolean visible = active && client != null && client.player != null && client.world != null
			&& ClientRoleRevealState.canShowRoleHud(client);
		alpha = MathHelper.lerp(0.22F, alpha, visible ? 1.0F : 0.0F);
		slide = MathHelper.lerp(0.18F, slide, visible ? 1.0F : 0.0F);
		pulse = Math.max(0.0F, pulse - 0.08F);
		if (client == null || client.world == null) {
			active = false;
			stage = 0;
		}
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.textRenderer == null || client.options.hudHidden || alpha <= 0.02F) return;
		TextRenderer text = client.textRenderer;
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
		int width = 118;
		int x = context.getScaledWindowWidth() - width - 10 + Math.round((1.0F - slide) * 22.0F);
		int y = 50;

		float scale = 1.0F + pulse * 0.08F;
		context.getMatrices().push();
		context.getMatrices().translate(x + width, y, 0.0F);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.getMatrices().translate(-(x + width), -y, 0.0F);

		int titleColor = withAlpha(0xE34A4A, a);
		int stageColor = withAlpha(0xFFD76A, a);
		int detailColor = withAlpha(0xF2F2F2, a);
		String title = "Juggernaut";
		String stageText = "Stage " + stage + "/" + maxStage;
		drawRight(context, text, title, x + width, y, titleColor);
		drawRight(context, text, stageText, x + width, y + 11, stageColor);

		int lineY = y + 26;
		for (String line : benefitLines()) {
			drawRight(context, text, line, x + width, lineY, detailColor);
			lineY += 10;
		}
		context.getMatrices().pop();
	}

	private static List<String> benefitLines() {
		List<String> lines = new ArrayList<>();
		if (stage <= 0) {
			lines.add("No momentum yet");
			return lines;
		}
		if (reductionSeconds > 0) {
			lines.add("-" + reductionSeconds + "s cooldowns");
		}
		if (gunShield) {
			lines.add("+ knife/gun shield");
		} else if (knifeShield) {
			lines.add("+ knife shield");
		}
		return lines;
	}

	private static void drawRight(DrawContext context, TextRenderer text, String value, int right, int y, int color) {
		context.drawTextWithShadow(text, value, right - text.getWidth(value), y, color);
	}

	private static int withAlpha(int color, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
	}
}
