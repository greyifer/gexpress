package dev.mapselect.mixin.client;

import dev.mapselect.client.role.puppetmaster.ClientPuppetmasterState;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class PuppetmasterKeyboardMixin {
	@Inject(method = "onKey", at = @At("TAIL"))
	private void gexpress$sendPuppetInputImmediately(long window, int key, int scanCode, int action,
			int modifiers, CallbackInfo ci) {
		ClientPuppetmasterState.sendImmediateInput(MinecraftClient.getInstance());
	}
}
