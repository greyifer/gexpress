package dev.mapselect.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.IPModInfoChecking;

@Mixin(value = IPModInfoChecking.class, remap = false)
public abstract class ImmersivePortalsModInfoCheckingMixin {
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Inject(method = "fetchImmPtlInfoFromInternet", at = @At("HEAD"), cancellable = true, remap = false)
	private static void gexpress$skipOnlineCompatibilityInfo(CallbackInfoReturnable cir) {
		cir.setReturnValue(null);
	}
}
