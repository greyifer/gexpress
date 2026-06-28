package dev.mapselect.mixin.client;

import dev.mapselect.client.role.puppetmaster.ClientPuppetmasterState;
import dev.mapselect.client.role.timemaster.ClientTimeMasterFreezeState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class PuppetmasterMouseMixin {
	@Shadow
	private double cursorDeltaX;
	@Shadow
	private double cursorDeltaY;

	@Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
	private void gexpress$lockControlledPlayerLook(double timeDelta, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientPuppetmasterState.isLocalTarget(client) || ClientTimeMasterFreezeState.isLocalFrozen(client)) {
			cursorDeltaX = 0.0D;
			cursorDeltaY = 0.0D;
			ci.cancel();
			return;
		}
		if (ClientPuppetmasterState.isLocalController(client)) {
			double sensitivity = client.options.getMouseSensitivity().getValue() * 0.6000000238418579D + 0.20000000298023224D;
			double multiplier = sensitivity * sensitivity * sensitivity * 8.0D;
			double deltaX = cursorDeltaX * multiplier;
			double deltaY = cursorDeltaY * multiplier * (client.options.getInvertYMouse().getValue() ? -1.0D : 1.0D);
			ClientPuppetmasterState.applyMouseLook(client, deltaX, deltaY);
			cursorDeltaX = 0.0D;
			cursorDeltaY = 0.0D;
			ci.cancel();
		}
	}

	@Inject(method = "updateMouse", at = @At("TAIL"))
	private void gexpress$syncPuppetLookImmediately(double timeDelta, CallbackInfo ci) {
		ClientPuppetmasterState.syncLookFromLocal(MinecraftClient.getInstance());
	}
}
