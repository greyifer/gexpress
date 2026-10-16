package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.client.game.ClientSpectatorRoleRevealDelay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HML and several Wathe addons inject role-specific instinct colors at method HEAD and may
 * cancel before another HEAD hook runs. Applying this mixin last and normalizing every return
 * prevents those callbacks from leaking role colors during G'Express's reveal delay.
 */
@Mixin(value = WatheClient.class, remap = false, priority = 100)
public abstract class WatheClientSpectatorColorFinalMixin {
	@Inject(method = "isInstinctEnabled", at = @At("RETURN"), cancellable = true)
	private static void gexpress$finalBellyInstinctVeto(CallbackInfoReturnable<Boolean> cir) {
		if (dev.mapselect.client.role.pelican.ClientVultureState.isLocalStashed(MinecraftClient.getInstance())) cir.setReturnValue(false);
	}
	@Inject(method = "getInstinctHighlight", at = @At("RETURN"), cancellable = true)
	private static void gexpress$maskDelayedSpectatorRoleColor(
			Entity target, CallbackInfoReturnable<Integer> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (dev.mapselect.client.role.pelican.ClientVultureState.isLocalStashed(client)) { cir.setReturnValue(-1); return; }
		if (!(target instanceof PlayerEntity player)
				|| !ClientSpectatorRoleRevealDelay.shouldMaskRoleColors(client)) {
			return;
		}
		if (GameFunctions.isPlayerSpectatingOrCreative(player)
				|| !ClientSpectatorRoleRevealDelay.shouldUseInstinctReveal(client)) {
			cir.setReturnValue(-1);
			return;
		}
		cir.setReturnValue(ClientSpectatorRoleRevealDelay.glowColor());
	}
}
