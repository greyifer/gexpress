package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.render.entity.PlayerBodyEntityRenderer;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.mapselect.client.role.painter.ClientPainterState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerBodyEntityRenderer.class, remap = false)
public abstract class PainterBodyRendererMixin {
	@Unique private static final ThreadLocal<PlayerBodyEntity> GEXPRESS_RENDERING_BODY = new ThreadLocal<>();

	@Inject(method = "render(Ldev/doctor4t/wathe/entity/PlayerBodyEntity;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V",
		at = @At("HEAD"),
		remap = false)
	private void gexpress$trackPainterBody(PlayerBodyEntity body, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		GEXPRESS_RENDERING_BODY.set(body);
	}

	@Inject(method = "render(Ldev/doctor4t/wathe/entity/PlayerBodyEntity;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V",
		at = @At("RETURN"),
		remap = false)
	private void gexpress$clearPainterBody(PlayerBodyEntity body, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		GEXPRESS_RENDERING_BODY.remove();
	}

	@ModifyArg(
		method = "render(Ldev/doctor4t/wathe/entity/PlayerBodyEntity;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;ILnet/minecraft/class_572;Lnet/minecraft/class_1921;FF)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/class_572;method_2828(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V"),
		index = 4,
		remap = false
	)
	private int gexpress$painterBodyColor(int originalColor) {
		return ClientPainterState.bodyColor(GEXPRESS_RENDERING_BODY.get(), originalColor);
	}
}
