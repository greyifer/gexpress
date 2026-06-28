package dev.mapselect.mixin.client;

import dev.mapselect.client.role.painter.ClientPainterDoorwayTransition;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class PainterDoorwayCameraMixin {
	@Inject(method = "update", at = @At("RETURN"))
	private void gexpress$applyPainterDoorwayTransition(BlockView area, Entity focusedEntity,
			boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
		Camera camera = (Camera) (Object) this;
		ClientPainterDoorwayTransition.checkClientCrossing(camera, tickDelta);
		ClientPainterDoorwayTransition.apply(camera, tickDelta);
	}
}
