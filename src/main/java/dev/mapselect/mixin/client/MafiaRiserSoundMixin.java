package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import dev.mapselect.client.role.mafia.ClientMafiaState;
import dev.mapselect.client.role.cupid.ClientCupidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RoundTextRenderer.class, remap = false)
public abstract class MafiaRiserSoundMixin {
	@Shadow private static int welcomeTime;

	@Inject(method = "tick", at = @At("HEAD"))
	private static void gexpress$skipWatheRiserForMafia(CallbackInfo ci) {
		if (welcomeTime == 200 && (ClientMafiaState.shouldSuppressWatheRiser()
				|| ClientCupidState.shouldSuppressWatheRiser())) {
			welcomeTime = 199;
		}
	}
}
