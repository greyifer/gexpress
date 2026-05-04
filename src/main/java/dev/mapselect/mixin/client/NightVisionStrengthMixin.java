package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientNightVisionState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class NightVisionStrengthMixin {
	@Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
	private static void gexpress$steadyModifierNightVision(LivingEntity entity, float tickDelta,
			CallbackInfoReturnable<Float> cir) {
		if (entity instanceof ClientPlayerEntity && ClientNightVisionState.isEnabled()) {
			cir.setReturnValue(1.0F);
		}
	}
}
