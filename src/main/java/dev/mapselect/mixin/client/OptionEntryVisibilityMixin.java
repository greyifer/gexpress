package dev.mapselect.mixin.client;

import dev.isxander.yacl3.api.Option;
import dev.mapselect.client.screen.OptionVisibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.isxander.yacl3.gui.OptionListWidget$OptionEntry", remap = false)
public abstract class OptionEntryVisibilityMixin {

	@Final
	@Shadow
	public Option<?> option;

	@Inject(method = "isViewable", at = @At("HEAD"), cancellable = true)
	private void gexpress$hideWhenPredicateMatches(CallbackInfoReturnable<Boolean> cir) {
		if (OptionVisibility.isHidden(option)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isViewable", at = @At("RETURN"), cancellable = true)
	private void gexpress$showWhenDropdownParentSearchMatches(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()
				&& !OptionVisibility.isHidden(option)
				&& OptionVisibility.isSearchAliasMatched(option)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "updateSearchQuery", at = @At("RETURN"), cancellable = true)
	private void gexpress$matchDropdownParentSearch(String query, CallbackInfoReturnable<Boolean> cir) {
		if (OptionVisibility.updateSearchAliasMatch(option, query)) {
			cir.setReturnValue(true);
		}
	}
}
