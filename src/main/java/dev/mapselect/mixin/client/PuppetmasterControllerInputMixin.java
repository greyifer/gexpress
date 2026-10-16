package dev.mapselect.mixin.client;

import dev.mapselect.client.role.puppetmaster.ClientPuppetmasterState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class PuppetmasterControllerInputMixin {
	@Inject(
		method = "tickMovement",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/input/Input;tick(ZF)V",
			shift = At.Shift.AFTER
		)
	)
	private void gexpress$keepControllerBodyStill(CallbackInfo ci) {
		ClientPuppetmasterState.lockControllerBodyInput(MinecraftClient.getInstance());
	}
}
