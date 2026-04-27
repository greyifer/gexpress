package dev.mapselect.mixin;

import cat.rezelyn.watheextended.game.PronounsManager;
import dev.mapselect.role.puppetmaster.PuppetmasterManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = PronounsManager.class, remap = false)
public abstract class PuppetmasterPronounsManagerMixin {
	@Inject(method = "get", at = @At("HEAD"), cancellable = true, remap = false)
	private static void gexpress$swapPuppetmasterPronouns(UUID playerId, CallbackInfoReturnable<String> cir) {
		UUID replacement = PuppetmasterManager.replacementFor(playerId);
		if (replacement != null) {
			cir.setReturnValue(PronounsManager.getAll().getOrDefault(replacement, ""));
		}
	}
}
