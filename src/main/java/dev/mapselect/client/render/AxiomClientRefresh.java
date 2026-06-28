package dev.mapselect.client.render;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;

public final class AxiomClientRefresh {
	private static final long LIGHT_REFRESH_THROTTLE_NANOS = 50_000_000L;
	private static final long FULL_REFRESH_THROTTLE_NANOS = 750_000_000L;
	private static int pendingLightPasses;
	private static int pendingFullPasses;
	private static long lastLightRefreshNanos;
	private static long lastFullRefreshNanos;

	private AxiomClientRefresh() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingLightPasses <= 0 && pendingFullPasses <= 0) return;
			boolean full = pendingFullPasses > 0;
			refresh(client, full);
			if (pendingLightPasses > 0) pendingLightPasses--;
			if (pendingFullPasses > 0) pendingFullPasses--;
		});
	}

	public static void requestLight() {
		request(false);
	}

	public static void requestFull() {
		request(true);
	}

	private static void request(boolean full) {
		if (!FabricLoader.getInstance().isModLoaded("axiom")) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) return;
		pendingLightPasses = Math.max(pendingLightPasses, 4);
		if (full) pendingFullPasses = Math.max(pendingFullPasses, 2);
		client.execute(() -> refresh(client, full));
	}

	private static void refresh(MinecraftClient client, boolean full) {
		if (client == null || client.world == null) return;
		long now = System.nanoTime();
		if (now - lastLightRefreshNanos < LIGHT_REFRESH_THROTTLE_NANOS && !full) return;
		lastLightRefreshNanos = now;

		clearAxiomChunkOverride();
		WorldRenderer renderer = client.worldRenderer;
		if (renderer == null) return;
		renderer.scheduleTerrainUpdate();
		if (client.player != null) {
			BlockPos pos = client.player.getBlockPos();
			renderer.scheduleBlockRenders(pos.getX() - 32, pos.getY() - 32, pos.getZ() - 32,
				pos.getX() + 32, pos.getY() + 32, pos.getZ() + 32);
		}
		if (full && now - lastFullRefreshNanos >= FULL_REFRESH_THROTTLE_NANOS) {
			lastFullRefreshNanos = now;
			renderer.reload();
		}
	}

	private static void clearAxiomChunkOverride() {
		try {
			Class<?> overrider = Class.forName("com.moulberry.axiom.render.ChunkRenderOverrider", false,
				AxiomClientRefresh.class.getClassLoader());
			Method clear = overrider.getMethod("clear");
			clear.invoke(null);
		} catch (Throwable ignored) {
		}
	}
}
