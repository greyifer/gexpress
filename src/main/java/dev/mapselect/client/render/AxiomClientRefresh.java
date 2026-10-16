package dev.mapselect.client.render;

import dev.mapselect.MapSelect;
import dev.mapselect.network.axiom.AxiomEditRefreshPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;

import java.lang.reflect.Method;

public final class AxiomClientRefresh {
	private static final long REFRESH_THROTTLE_NANOS = 100_000_000L;
	private static int pendingPasses;
	private static long lastRefreshNanos;
	private static boolean loggedRefreshFailure;

	private AxiomClientRefresh() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AxiomEditRefreshPayload.ID, (payload, context) ->
			context.client().execute(AxiomClientRefresh::requestAxiomEditRefresh));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingPasses <= 0) return;
			refresh(client);
			pendingPasses--;
		});
	}

	public static void requestLight() {
		requestAxiomEditRefresh();
	}

	public static void requestFull() {
		requestAxiomEditRefresh();
	}

	public static void requestAxiomEditRefresh() {
		if (!FabricLoader.getInstance().isModLoaded("axiom")) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) return;
		pendingPasses = Math.max(pendingPasses, 2);
		client.execute(() -> refresh(client));
	}

	private static void refresh(MinecraftClient client) {
		if (client == null || client.world == null) return;
		long now = System.nanoTime();
		if (now - lastRefreshNanos < REFRESH_THROTTLE_NANOS) return;
		lastRefreshNanos = now;

		clearAxiomChunkOverride();
		WorldRenderer renderer = client.worldRenderer;
		if (renderer == null) return;
		renderer.scheduleTerrainUpdate();
	}

	private static void clearAxiomChunkOverride() {
		try {
			Class<?> overrider = Class.forName("com.moulberry.axiom.render.ChunkRenderOverrider", false,
				AxiomClientRefresh.class.getClassLoader());
			Method clear = overrider.getMethod("clear");
			clear.invoke(null);
		} catch (Throwable error) {
			if (!loggedRefreshFailure) {
				loggedRefreshFailure = true;
				MapSelect.LOGGER.warn("Axiom is loaded, but G'Express could not call its chunk refresh API. Edits may need a manual chunk refresh.", error);
			}
		}
	}
}
