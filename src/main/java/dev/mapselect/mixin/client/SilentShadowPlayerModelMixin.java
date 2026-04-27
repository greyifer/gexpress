package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientSilentShadowState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class SilentShadowPlayerModelMixin {
	@Inject(method = "setModelPose", at = @At("RETURN"))
	private void gexpress$hideOuterLayerDuringShadow(AbstractClientPlayerEntity player, CallbackInfo ci) {
		if (!ClientSilentShadowState.isShadowed(player)) return;

		PlayerEntityModel<AbstractClientPlayerEntity> model =
			((PlayerEntityRenderer) (Object) this).getModel();
		hide(model.hat);
		hide(model.jacket);
		hide(model.leftSleeve);
		hide(model.rightSleeve);
		hide(model.leftPants);
		hide(model.rightPants);
	}

	private static void hide(ModelPart part) {
		part.visible = false;
	}
}
