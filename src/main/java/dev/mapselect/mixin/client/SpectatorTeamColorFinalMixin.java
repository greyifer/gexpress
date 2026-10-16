package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.client.game.ClientSpectatorRoleRevealDelay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Final outline-color guard for renderer paths that read the entity team color directly.
 */
@Mixin(value = Entity.class, priority = 100)
public abstract class SpectatorTeamColorFinalMixin {
	@Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true)
	private void gexpress$maskDelayedSpectatorTeamColor(CallbackInfoReturnable<Integer> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!((Object) this instanceof AbstractClientPlayerEntity player)
				|| GameFunctions.isPlayerSpectatingOrCreative(player)
				|| !ClientSpectatorRoleRevealDelay.shouldMaskRoleColors(client)) {
			return;
		}
		cir.setReturnValue(ClientSpectatorRoleRevealDelay.glowColor());
	}
}
