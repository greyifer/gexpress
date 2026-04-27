package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientPuppetmasterState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class PuppetmasterMouseMixin {
	@Inject(method = "updateMouse", at = @At("TAIL"))
	private void gexpress$syncPuppetLookImmediately(double timeDelta, CallbackInfo ci) {
		ClientPuppetmasterState.syncLookFromLocal(MinecraftClient.getInstance());
	}
}
