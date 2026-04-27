package dev.mapselect.client;

import dev.mapselect.network.MedicShieldFlashPayload;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.role.medic.MedicShieldComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;

import java.util.UUID;

public final class ClientMedicShieldState {
	public static final int SHIELD_COLOR = 0x2CFF72;

	private static UUID shieldTargetUuid;
	private static int flashTicks;
	private static int flashMaxTicks;
	private static int flashMaxAlpha;

	private ClientMedicShieldState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MedicShieldFlashPayload.ID, (payload, context) ->
			context.client().execute(() -> {
				flashTicks = payload.broken()
					? GexpressConfig.getMedicShieldBreakFlashTicks()
					: GexpressConfig.getMedicShieldBlockFlashTicks();
				flashMaxTicks = flashTicks;
				flashMaxAlpha = payload.broken()
					? GexpressConfig.getMedicShieldBreakFlashAlpha()
					: GexpressConfig.getMedicShieldBlockFlashAlpha();
			}));

		ClientTickEvents.END_CLIENT_TICK.register(ClientMedicShieldState::tick);
		HudRenderCallback.EVENT.register(ClientMedicShieldState::renderOverlay);
	}

	public static boolean shouldGlow(Entity entity) {
		return entity != null && shieldTargetUuid != null && shieldTargetUuid.equals(entity.getUuid());
	}

	private static void tick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			shieldTargetUuid = null;
			flashTicks = 0;
			flashMaxTicks = 0;
			flashMaxAlpha = 0;
			return;
		}

		MedicShieldComponent comp = MedicShieldComponent.KEY.getNullable(client.world);
		shieldTargetUuid = comp == null ? null : comp.getTargetForMedic(client.player.getUuid());
		if (flashTicks > 0) {
			flashTicks--;
		}
	}

	private static void renderOverlay(DrawContext context, RenderTickCounter tickCounter) {
		if (flashTicks <= 0 || flashMaxTicks <= 0) return;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getWindow() == null) return;

		float progress = flashTicks / (float) flashMaxTicks;
		int alpha = Math.max(0, Math.min(255, Math.round(flashMaxAlpha * progress)));
		int color = (alpha << 24) | 0x00FF66;
		context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), color);
	}
}
