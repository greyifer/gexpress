package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientMafiaState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public abstract class MafiaSkyFlashMixin {
	@Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
	private void gexpress$mafiaLightningSky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		if (!ClientMafiaState.shouldUseMafiaWeather()) return;
		Vec3d color = cir.getReturnValue();
		float flash = ClientMafiaState.mafiaLightningStrength();
		if (flash <= 0.0F) {
			cir.setReturnValue(color.multiply(0.62D, 0.68D, 0.78D));
			return;
		}
		Vec3d storm = color.multiply(0.62D, 0.68D, 0.78D);
		Vec3d lightning = new Vec3d(0.92D, 0.94D, 1.0D);
		cir.setReturnValue(storm.multiply(1.0D - flash).add(lightning.multiply(flash)));
	}

	@Inject(method = "getSkyBrightness", at = @At("RETURN"), cancellable = true)
	private void gexpress$mafiaLightningBrightness(float tickDelta, CallbackInfoReturnable<Float> cir) {
		if (!ClientMafiaState.shouldUseMafiaWeather()) return;
		float flash = ClientMafiaState.mafiaLightningStrength();
		cir.setReturnValue(Math.min(1.0F, cir.getReturnValueF() * 0.7F + flash * 0.45F));
	}
}
