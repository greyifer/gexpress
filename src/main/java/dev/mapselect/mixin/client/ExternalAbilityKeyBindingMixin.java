package dev.mapselect.mixin.client;

import dev.mapselect.client.input.ClientExternalAbilityKeys;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public abstract class ExternalAbilityKeyBindingMixin {
	@Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
	private void gexpress$usePrimaryAbilityPressed(CallbackInfoReturnable<Boolean> cir) {
		Boolean pressed = ClientExternalAbilityKeys.isPressed((KeyBinding) (Object) this);
		if (pressed != null) cir.setReturnValue(pressed);
	}

	@Inject(method = "wasPressed", at = @At("HEAD"), cancellable = true)
	private void gexpress$usePrimaryAbilityWasPressed(CallbackInfoReturnable<Boolean> cir) {
		Boolean pressed = ClientExternalAbilityKeys.wasPressed((KeyBinding) (Object) this);
		if (pressed != null) cir.setReturnValue(pressed);
	}
}
