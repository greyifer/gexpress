package dev.mapselect.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Pseudo
@Mixin(targets = "org.agmas.noellesroles.packet.GuessC2SPacket", remap = false)
public abstract class NoellesGuessPacketMixin {
	@Inject(method = "guess()Ljava/lang/String;", at = @At("RETURN"), cancellable = true)
	private void gexpress$blockSnitchGuess(CallbackInfoReturnable<String> cir) {
		String guess = cir.getReturnValue();
		if (guess == null) return;
		String normalized = guess.trim().toLowerCase(Locale.ROOT);
		if ("snitch".equals(normalized) || "gexpress:snitch".equals(normalized)) {
			cir.setReturnValue(null);
		}
	}
}
