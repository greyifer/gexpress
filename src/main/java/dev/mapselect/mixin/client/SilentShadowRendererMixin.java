package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientSilentShadowState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class SilentShadowRendererMixin<T extends LivingEntity> {
	@Unique private static final ThreadLocal<LivingEntity> GEXPRESS_RENDERING_ENTITY = new ThreadLocal<>();

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At("HEAD"))
	private void gexpress$trackShadowEntity(T entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		GEXPRESS_RENDERING_ENTITY.set(entity);
	}

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At("RETURN"))
	private void gexpress$clearShadowEntity(T entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		GEXPRESS_RENDERING_ENTITY.remove();
	}

	@Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
	private void gexpress$shadowRenderLayer(T entity, boolean showBody, boolean translucent,
			boolean showOutline, CallbackInfoReturnable<RenderLayer> cir) {
		if (entity instanceof PlayerEntity && ClientSilentShadowState.isShadowed(entity)) {
			@SuppressWarnings("unchecked")
			EntityRenderer<T> renderer = (EntityRenderer<T>) (Object) this;
			cir.setReturnValue(RenderLayer.getEntityTranslucent(renderer.getTexture(entity)));
		}
	}

	@Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void gexpress$hideShadowLabel(T entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof PlayerEntity && ClientSilentShadowState.isShadowed(entity)) {
			cir.setReturnValue(false);
		}
	}

	@ModifyArg(
		method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"),
		index = 4
	)
	private int gexpress$shadowColor(int originalColor) {
		LivingEntity entity = GEXPRESS_RENDERING_ENTITY.get();
		if (entity instanceof PlayerEntity && ClientSilentShadowState.isShadowed(entity)) {
			return ClientSilentShadowState.shadowColor();
		}
		return originalColor;
	}
}
