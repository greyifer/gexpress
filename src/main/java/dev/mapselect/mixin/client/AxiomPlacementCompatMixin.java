package dev.mapselect.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.clipboard.Placement", remap = false)
public abstract class AxiomPlacementCompatMixin {
	private static long gexpress$lastTerrainRefreshNanos;

	@Inject(method = "pastePlacement()V", at = @At("RETURN"), require = 0, remap = false)
	private void gexpress$afterPastePlacement(CallbackInfo ci) {
		gexpress$scheduleAxiomTerrainRefresh();
	}

	@Inject(method = "pastePlacement(Z)V", at = @At("RETURN"), require = 0, remap = false)
	private void gexpress$afterPastePlacement(boolean keepPlacement, CallbackInfo ci) {
		gexpress$scheduleAxiomTerrainRefresh();
	}

	@Inject(method = "pastePlacementWithoutStopping(Z)V", at = @At("RETURN"), require = 0, remap = false)
	private void gexpress$afterPastePlacementWithoutStopping(boolean sendFeedback, CallbackInfo ci) {
		gexpress$scheduleAxiomTerrainRefresh();
	}

	@Inject(method = "stopPlacement()V", at = @At("RETURN"), require = 0, remap = false)
	private void gexpress$afterStopPlacement(CallbackInfo ci) {
		gexpress$scheduleAxiomTerrainRefresh();
	}

	private static void gexpress$scheduleAxiomTerrainRefresh() {
		if (!FabricLoader.getInstance().isModLoaded("axiom")) return;
		long now = System.nanoTime();
		if (now - gexpress$lastTerrainRefreshNanos < 50_000_000L) return;
		gexpress$lastTerrainRefreshNanos = now;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) return;
		client.execute(() -> {
			gexpress$clearAxiomChunkOverride();
			if (client.worldRenderer != null) {
				client.worldRenderer.scheduleTerrainUpdate();
			}
		});
	}

	private static void gexpress$clearAxiomChunkOverride() {
		try {
			Class<?> overrider = Class.forName("com.moulberry.axiom.render.ChunkRenderOverrider", false,
				AxiomPlacementCompatMixin.class.getClassLoader());
			Method clear = overrider.getMethod("clear");
			clear.invoke(null);
		} catch (Throwable ignored) {
		}
	}
}
