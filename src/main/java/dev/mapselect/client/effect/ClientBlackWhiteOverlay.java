package dev.mapselect.client.effect;

import dev.mapselect.MapSelect;
import dev.mapselect.client.role.cupid.ClientCupidState;
import dev.mapselect.client.role.mafia.ClientMafiaState;
import dev.mapselect.network.game.TestOverlayPayload;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class ClientBlackWhiteOverlay {
	private static final Identifier VEIL_PIPELINE = Identifier.of(MapSelect.MOD_ID, "screen_overlay");
	private static final int MODE_MAFIA = 0;
	private static final int MODE_CUPID = 1;
    private static final int MODE_LOKI = 2;

	private static int testMode = -1;
	private static long testModeUntilTick;
	private static boolean veilPipelineActive;
	private static boolean veilRenderingCurrentEffect;
	private static boolean loggedVeilFailure;

	private ClientBlackWhiteOverlay() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TestOverlayPayload.ID, (payload, context) ->
			context.client().execute(() -> applyTestOverlay(context.client(), payload)));
		WorldRenderEvents.START.register(ClientBlackWhiteOverlay::prepareFrame);
		ClientTickEvents.END_CLIENT_TICK.register(ClientBlackWhiteOverlay::tick);
		HudRenderCallback.EVENT.register(ClientBlackWhiteOverlay::renderFallback);
	}

	private static void prepareFrame(WorldRenderContext context) {
		float tickDelta = context.tickCounter() == null ? 0.0F
			: context.tickCounter().getTickDelta(true);
		updateVeilPipeline(MinecraftClient.getInstance(), tickDelta);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.world == null) disableVeilPipeline();
		else activeTestMode(client);
	}

	private static void renderFallback(DrawContext context, RenderTickCounter tickCounter) {
		if (veilRenderingCurrentEffect) return;
		MinecraftClient client = MinecraftClient.getInstance();
		float tickDelta = tickCounter == null ? 0.0F : tickCounter.getTickDelta(true);
		OverlayEffect effect = desiredEffect(client, tickDelta);
		if (effect == null || effect.strength <= 0.02F) return;

		int maxAlpha = effect.mode == MODE_CUPID ? 38 : 76;
		int alpha = MathHelper.clamp(Math.round(maxAlpha * effect.strength), 0, maxAlpha);
		int rgb = effect.mode == MODE_CUPID ? 0xFF8FC5 : 0x000000;
		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(),
			(alpha << 24) | rgb);
	}

	private static void updateVeilPipeline(MinecraftClient client, float tickDelta) {
		OverlayEffect effect = desiredEffect(client, tickDelta);
		if (effect == null || effect.strength <= 0.001F) {
			disableVeilPipeline();
			return;
		}
		try {
			PostProcessingManager manager = VeilRenderSystem.renderer().getPostProcessingManager();
			PostPipeline pipeline = manager.getPipeline(VEIL_PIPELINE);
			if (pipeline == null) {
				veilRenderingCurrentEffect = false;
				return;
			}
			if (!manager.isActive(VEIL_PIPELINE) && !manager.add(VEIL_PIPELINE)) {
				veilRenderingCurrentEffect = false;
				return;
			}
			pipeline.getUniformSafe("Mode").setInt(effect.mode);
			pipeline.getUniformSafe("Strength").setFloat(effect.strength);
            pipeline.getUniformSafe("Time").setFloat(client.world.getTime() + tickDelta);
			veilPipelineActive = true;
			veilRenderingCurrentEffect = true;
		} catch (Throwable error) {
			try {
				VeilRenderSystem.renderer().getPostProcessingManager().remove(VEIL_PIPELINE);
			} catch (Throwable ignored) {
				// The fallback below is safe even when Veil itself is unavailable.
			}
			veilPipelineActive = false;
			veilRenderingCurrentEffect = false;
			if (!loggedVeilFailure) {
				loggedVeilFailure = true;
				MapSelect.LOGGER.warn("Could not initialize G'Express Veil screen effects; using HUD fallback.", error);
			}
		}
	}

	public static boolean lokiWarpRendered() { return veilRenderingCurrentEffect && dev.mapselect.client.role.loki.ClientLokiState.hexed(); }

	private static void disableVeilPipeline() {
		veilRenderingCurrentEffect = false;
		if (!veilPipelineActive) return;
		try {
			VeilRenderSystem.renderer().getPostProcessingManager().remove(VEIL_PIPELINE);
		} catch (Throwable ignored) {
			// Veil may already be shutting down during a world or resource transition.
		}
		veilPipelineActive = false;
	}

	private static OverlayEffect desiredEffect(MinecraftClient client, float tickDelta) {
		
		int test = activeTestMode(client);
		if (test >= 0) return new OverlayEffect(test, 1.0F);
		float cupid = ClientCupidState.cupidOverlayStrength();
		if (cupid > 0.001F) return new OverlayEffect(MODE_CUPID, cupid);
		float mafia = ClientMafiaState.blackWhiteStrength();
		return mafia > 0.001F ? new OverlayEffect(MODE_MAFIA, mafia) : null;
	}

	private static void applyTestOverlay(MinecraftClient client, TestOverlayPayload payload) {
		if (payload.durationTicks() <= 0 || "clear".equals(payload.overlay())) {
			testMode = -1;
			testModeUntilTick = 0L;
			return;
		}
		if (client == null || client.world == null) return;
		testMode = switch (payload.overlay()) {
			case "mafia", "black_white" -> MODE_MAFIA;
			case "cupid", "pink", "cupid_pink" -> MODE_CUPID;
			default -> -1;
		};
		testModeUntilTick = testMode < 0 ? 0L : client.world.getTime() + payload.durationTicks();
	}

	private static int activeTestMode(MinecraftClient client) {
		if (testMode < 0) return -1;
		if (client == null || client.world == null || client.world.getTime() >= testModeUntilTick) {
			testMode = -1;
			testModeUntilTick = 0L;
			return -1;
		}
		return testMode;
	}

	private record OverlayEffect(int mode, float strength) {}
}
