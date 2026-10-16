package dev.mapselect.mixin.client;

import dev.mapselect.client.role.rat.ClientRatState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class RatPlayerRendererMixin {
	@Inject(
		method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void gexpress$hideInvisibleRat(AbstractClientPlayerEntity player, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if ((ClientRatState.isInvisibleRat(player) && !ClientRatState.isHuntedRat(player))
				|| (player.isSpectator() && ClientRatState.isRat(player))) {
			ci.cancel();
		}
	}
}
