package dev.mapselect.mixin.client;

import dev.mapselect.client.role.rat.ClientRatState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class RatHeldItemRendererMixin {
	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void gexpress$hideInvisibleRatHand(AbstractClientPlayerEntity player,
			float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item,
			float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, CallbackInfo ci) {
		if (ClientRatState.isLocalInvisible()) ci.cancel();
	}
}
