package dev.mapselect.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents a transitional multiplayer frame from updating the camera with a null entity. */
@Mixin(GameRenderer.class)
public abstract class GameRendererCameraNullGuardMixin {
	@Inject(method = "renderWorld", at = @At("HEAD"), cancellable = true)
	private void gexpress$guardMissingCameraEntity(RenderTickCounter tickCounter, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getCameraEntity() != null) return;
		if (client.player != null) {
			client.setCameraEntity(client.player);
			return;
		}
		ci.cancel();
	}
}
