package dev.mapselect.mixin;

import dev.mapselect.role.veteran.NoellesGuessCancellation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes Noelle's normal Guesser cooldown when G'Express cancelled the guess. */
@Pseudo
@Mixin(targets = "org.agmas.noellesroles.AbilityPlayerComponent", remap = false)
public abstract class NoellesAbilityCooldownMixin {
	@Shadow public int cooldown;

	@Inject(method = "sync()V", at = @At("HEAD"))
	private void gexpress$clearCancelledGuessCooldown(CallbackInfo ci) {
		if (NoellesGuessCancellation.consumeBlocked()) {
			cooldown = 0;
		}
	}
}
