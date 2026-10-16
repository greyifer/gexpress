package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mapselect.client.role.twins.ClientTwinsFeedRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class TwinsCameraFeedRenderMixin {
	@WrapOperation(
		method = "renderWorld",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"
		)
	)
	private void gexpress$renderTwinsFeedBeforeMainWorld(WorldRenderer worldRenderer,
			RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera,
			GameRenderer gameRenderer, LightmapTextureManager lightmap,
			Matrix4f positionMatrix, Matrix4f projectionMatrix, Operation<Void> original) {
		ClientTwinsFeedRenderer.renderBeforeMainWorld(worldRenderer, tickCounter, camera,
			gameRenderer, lightmap, positionMatrix, projectionMatrix);
		original.call(worldRenderer, tickCounter, renderBlockOutline, camera, gameRenderer,
			lightmap, positionMatrix, projectionMatrix);
	}
}
