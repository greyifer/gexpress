package dev.mapselect.client;

import dev.mapselect.network.TimeMasterRewindPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class ClientTimeMasterRewindState {
	private static long activeUntilTick;
	private static int activeDurationTicks;

	private ClientTimeMasterRewindState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TimeMasterRewindPayload.ID, (payload, context) ->
			context.client().execute(() -> start(context.client(), payload.durationTicks())));
		HudRenderCallback.EVENT.register(ClientTimeMasterRewindState::render);
	}

	private static void start(MinecraftClient client, int durationTicks) {
		if (client == null || client.world == null) return;
		activeDurationTicks = Math.max(1, durationTicks);
		activeUntilTick = client.world.getTime() + activeDurationTicks;
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || activeUntilTick <= client.world.getTime()) return;
		long remaining = activeUntilTick - client.world.getTime();
		float phase = Math.min(1.0F, Math.max(0.0F, remaining / (float) Math.max(1, activeDurationTicks)));
		int alpha = 42 + Math.round(54.0F * phase);
		int color = (alpha << 24) | 0x6ADFFF;
		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), color);
	}
}
