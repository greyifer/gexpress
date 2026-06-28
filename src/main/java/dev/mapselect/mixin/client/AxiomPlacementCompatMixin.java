package dev.mapselect.mixin.client;

import dev.mapselect.client.render.AxiomClientRefresh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.clipboard.Placement", remap = false)
public abstract class AxiomPlacementCompatMixin {
	@Inject(method = "pastePlacement()V", at = @At("RETURN"), require = 0, remap = false)
	private void gexpress$afterPastePlacement(CallbackInfo ci) {
		AxiomClientRefresh.requestFull();
	}

	@Inject(method = "pastePlacement(Z)V", at = @At("RETURN"), require = 0, remap = false)
	private void gexpress$afterPastePlacement(boolean keepPlacement, CallbackInfo ci) {
		AxiomClientRefresh.requestFull();
	}

	@Inject(method = "pastePlacementWithoutStopping(Z)V", at = @At("RETURN"), require = 0, remap = false)
	private void gexpress$afterPastePlacementWithoutStopping(boolean sendFeedback, CallbackInfo ci) {
		AxiomClientRefresh.requestFull();
	}

	@Inject(method = "stopPlacement()V", at = @At("RETURN"), require = 0, remap = false)
	private void gexpress$afterStopPlacement(CallbackInfo ci) {
		AxiomClientRefresh.requestFull();
	}
}
