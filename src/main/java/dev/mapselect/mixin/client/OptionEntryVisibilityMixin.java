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

	@Shadow
	protected boolean searchQueryMatches;

	@Inject(method = "isViewable", at = @At("HEAD"), cancellable = true)
	private void gexpress$hideWhenPredicateMatches(CallbackInfoReturnable<Boolean> cir) {
		if (OptionVisibility.isHidden(option)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "updateSearchQuery", at = @At("HEAD"), cancellable = true)
	private void gexpress$matchDropdownParentSearch(String query, CallbackInfoReturnable<Boolean> cir) {
		if (OptionVisibility.matchesSearchAlias(option, query)) {
			this.searchQueryMatches = true;
			cir.setReturnValue(true);
		}
	}
}
