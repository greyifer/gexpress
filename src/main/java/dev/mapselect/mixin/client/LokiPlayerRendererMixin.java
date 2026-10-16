package dev.mapselect.mixin.client;

import dev.mapselect.role.loki.LokiGravity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class LokiPlayerRendererMixin {
    @Inject(method="setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",at=@At("TAIL"))
    private void gexpress$renderUpsideDown(AbstractClientPlayerEntity player,MatrixStack matrices,float animationProgress,float bodyYaw,float tickDelta,float scale,CallbackInfo ci){if(LokiGravity.flipped(player)){matrices.translate(0,player.getHeight()/scale,0);matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));}}
}
