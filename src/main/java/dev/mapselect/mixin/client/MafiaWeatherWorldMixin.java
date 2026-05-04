package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientMafiaState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class MafiaWeatherWorldMixin {
	@Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
	private void gexpress$mafiaRain(float tickDelta, CallbackInfoReturnable<Float> cir) {
		if (ClientMafiaState.shouldUseMafiaWeather()) {
			cir.setReturnValue(ClientMafiaState.mafiaRainGradient());
		}
	}

	@Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
	private void gexpress$mafiaThunder(float tickDelta, CallbackInfoReturnable<Float> cir) {
		if (ClientMafiaState.shouldUseMafiaWeather()) {
			cir.setReturnValue(ClientMafiaState.mafiaThunderGradient());
		}
	}

	@Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
	private void gexpress$mafiaIsRaining(CallbackInfoReturnable<Boolean> cir) {
		if (ClientMafiaState.shouldUseMafiaWeather()) cir.setReturnValue(true);
	}

	@Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
	private void gexpress$mafiaIsThundering(CallbackInfoReturnable<Boolean> cir) {
		if (ClientMafiaState.shouldUseMafiaWeather()) cir.setReturnValue(true);
	}

	@Inject(method = "hasRain", at = @At("HEAD"), cancellable = true)
	private void gexpress$mafiaHasRain(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (ClientMafiaState.shouldUseMafiaWeather()) cir.setReturnValue(true);
	}
}
