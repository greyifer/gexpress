package dev.mapselect.mixin;

import dev.mapselect.role.snitch.SnitchManager;
import dev.mapselect.role.veteran.NoellesGuessCancellation;
import dev.mapselect.role.veteran.VeteranManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.UUID;

@Pseudo
@Mixin(targets = "org.agmas.noellesroles.packet.GuessC2SPacket", remap = false)
public abstract class NoellesGuessPacketMixin {
	@Shadow
	public abstract UUID player();

	@Inject(method = "guess()Ljava/lang/String;", at = @At("RETURN"), cancellable = true)
	private void gexpress$blockSnitchGuess(CallbackInfoReturnable<String> cir) {
		if (VeteranManager.isAlertActive(player())) {
			NoellesGuessCancellation.markBlocked();
			cir.setReturnValue(null);
			return;
		}
		String guess = cir.getReturnValue();
		if (guess == null) return;
		String normalized = guess.trim().toLowerCase(Locale.ROOT);
		if (("snitch".equals(normalized) || "gexpress:snitch".equals(normalized))
				&& SnitchManager.isExposedToKillers(player())) {
			NoellesGuessCancellation.markBlocked();
			cir.setReturnValue(null);
		}
	}
}
