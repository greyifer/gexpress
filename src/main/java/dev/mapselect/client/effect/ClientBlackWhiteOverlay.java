package dev.mapselect.client.effect;

import dev.mapselect.MapSelect;
import dev.mapselect.client.role.cupid.ClientCupidState;
import dev.mapselect.client.role.mafia.ClientMafiaState;
import dev.mapselect.mixin.client.GameRendererAccessor;
import dev.mapselect.network.game.TestOverlayPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class ClientBlackWhiteOverlay {
	private static final Identifier MAFIA_SHADER =
		Identifier.of(MapSelect.MOD_ID, "shaders/post/mafia_black_white.json");
	private static final Identifier CUPID_SHADER =
		Identifier.of(MapSelect.MOD_ID, "shaders/post/cupid_pink.json");

	private static boolean shaderActive;
	private static Identifier activeShader;
	private static Identifier testShader;
	private static long testShaderUntilTick;

	private ClientBlackWhiteOverlay() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TestOverlayPayload.ID, (payload, context) ->
			context.client().execute(() -> applyTestOverlay(context.client(), payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientBlackWhiteOverlay::tick);
		HudRenderCallback.EVENT.register(ClientBlackWhiteOverlay::render);
	}

	private static void tick(MinecraftClient client) {
		updateShader(client);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		float strength = overlayStrength(client);
		if (strength <= 0.02F) return;
		boolean cupid = isCupidOverlay(client);
		int alpha = Math.max(0, Math.min(cupid ? 24 : 76, Math.round((cupid ? 24.0F : 76.0F) * strength)));
		int rgb = cupid ? 0xFFD8EA : 0x000000;
		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), (alpha << 24) | rgb);
	}

	private static void updateShader(MinecraftClient client) {
		Identifier desiredShader = desiredShader(client);
		if (client == null || client.gameRenderer == null) {
			shaderActive = false;
			activeShader = null;
			return;
		}
		if (desiredShader != null && shaderActive && client.gameRenderer.getPostProcessor() == null) {
			shaderActive = false;
			activeShader = null;
		}
		if (desiredShader == null) {
			disable(client);
			return;
		}
		if (!desiredShader.equals(activeShader)) {
			disable(client);
			load(client, desiredShader);
		} else if (!shaderActive) {
			load(client, desiredShader);
		}
		updateShaderStrength(client);
	}

	private static Identifier desiredShader(MinecraftClient client) {
		Identifier test = activeTestShader(client);
		if (test != null) return test;
		if (ClientCupidState.cupidOverlayStrength() > 0.02F) return CUPID_SHADER;
		return ClientMafiaState.blackWhiteStrength() > 0.02F ? MAFIA_SHADER : null;
	}

	private static float overlayStrength(MinecraftClient client) {
		if (activeTestShader(client) != null) return 1.0F;
		if (ClientCupidState.cupidOverlayStrength() > 0.02F) return ClientCupidState.cupidOverlayStrength();
		return ClientMafiaState.blackWhiteStrength();
	}

	private static boolean isCupidOverlay(MinecraftClient client) {
		Identifier test = activeTestShader(client);
		return CUPID_SHADER.equals(test) || (test == null && ClientCupidState.cupidOverlayStrength() > 0.02F);
	}

	private static void applyTestOverlay(MinecraftClient client, TestOverlayPayload payload) {
		if (payload.durationTicks() <= 0 || "clear".equals(payload.overlay())) {
			testShader = null;
			testShaderUntilTick = 0L;
			return;
		}
		if (client == null || client.world == null) return;
		testShader = switch (payload.overlay()) {
			case "mafia", "black_white" -> MAFIA_SHADER;
			case "cupid", "pink", "cupid_pink" -> CUPID_SHADER;
			default -> null;
		};
		testShaderUntilTick = testShader == null ? 0L : client.world.getTime() + payload.durationTicks();
	}

	private static Identifier activeTestShader(MinecraftClient client) {
		if (testShader == null) return null;
		if (client == null || client.world == null || client.world.getTime() >= testShaderUntilTick) {
			testShader = null;
			testShaderUntilTick = 0L;
			return null;
		}
		return testShader;
	}

	private static void load(MinecraftClient client, Identifier shader) {
		try {
			((GameRendererAccessor) client.gameRenderer).gexpress$loadPostProcessor(shader);
			shaderActive = true;
			activeShader = shader;
		} catch (Throwable ignored) {
			shaderActive = false;
			activeShader = null;
		}
	}

	private static void disable(MinecraftClient client) {
		if (!shaderActive || client == null || client.gameRenderer == null) return;
		client.gameRenderer.disablePostProcessor();
		shaderActive = false;
		activeShader = null;
	}

	private static void updateShaderStrength(MinecraftClient client) {
		if (!shaderActive || client == null || client.gameRenderer == null
				|| client.gameRenderer.getPostProcessor() == null) {
			return;
		}
		float desaturation = CUPID_SHADER.equals(activeShader) ? 0.9F : 1.0F;
		client.gameRenderer.getPostProcessor().setUniforms("Saturation",
			MathHelper.clamp(1.0F - overlayStrength(client) * desaturation, 0.0F, 1.0F));
	}
}
