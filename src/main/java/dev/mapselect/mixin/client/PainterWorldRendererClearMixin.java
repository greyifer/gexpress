package dev.mapselect.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mapselect.client.render.ClientPainterDoorwayRenderer;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldRenderer.class)
public abstract class PainterWorldRendererClearMixin {
	@Redirect(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"
		),
		require = 0
	)
	private void gexpress$skipClearDuringPainterDoorway(int mask, boolean getError) {
		if (!ClientPainterDoorwayRenderer.shouldSkipPortalWorldClear()) {
			RenderSystem.clear(mask, getError);
		}
	}
}
