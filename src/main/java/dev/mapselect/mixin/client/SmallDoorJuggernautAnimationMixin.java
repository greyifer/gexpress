package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.client.render.block_entity.SmallDoorBlockEntityRenderer;
import dev.mapselect.client.render.ClientJuggernautDoorAnimation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmallDoorBlockEntityRenderer.class)
public abstract class SmallDoorJuggernautAnimationMixin {
	@Shadow @Final private ModelPart part;

	@Inject(method = "setAngles", at = @At("HEAD"), cancellable = true)
	private void gexpress$playBrokenDoorAnimation(SmallDoorBlockEntity entity, float animationProgress,
			CallbackInfo ci) {
		if (entity == null) return;
		if (!entity.isBlasted() || !ClientJuggernautDoorAnimation.supports(entity)) {
			ClientJuggernautDoorAnimation.clear(entity);
			return;
		}
		// Wathe normally runs its sideways OPEN animation here. A blasted door instead starts
		// from the untouched model root and applies the supplied keyframes to the Door child.
		this.part.traverse().forEach(ModelPart::resetTransform);
		ClientJuggernautDoorAnimation.applySmallDoor(
			(SinglePartEntityModel<?>) (Object) this, entity, animationProgress);
		ci.cancel();
	}
}
