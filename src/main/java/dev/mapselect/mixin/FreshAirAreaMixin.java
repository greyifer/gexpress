package dev.mapselect.mixin;

import dev.doctor4t.wathe.Wathe;
import dev.mapselect.task.FreshAirAreaManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Wathe.class, remap = false)
public abstract class FreshAirAreaMixin {
	@Inject(method = "isSkyVisibleAdjacent", at = @At("HEAD"), cancellable = true)
	private static void gexpress$freshAirAreaCountsAsOutside(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (FreshAirAreaManager.hasFreshAirTag(entity) || FreshAirAreaManager.countsAsFreshAir(entity)) {
			cir.setReturnValue(true);
		}
	}
}
