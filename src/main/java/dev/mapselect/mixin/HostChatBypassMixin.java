package dev.mapselect.mixin;

import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.role.pelican.PelicanManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Run after Wathe Extended's default-priority mixin so its injected helper exists to target.
@Mixin(value = ServerPlayNetworkHandler.class, priority = 500)
public abstract class HostChatBypassMixin {
	@Dynamic("Wathe Extended injects this helper method into ServerPlayNetworkHandler.")
	@Inject(
		method = "watheextended$isGameRunningAndNotOp",
		at = @At("HEAD"),
		cancellable = true,
		remap = false,
		require = 0
	)
	private void mapselect$hostBypass(CallbackInfoReturnable<Boolean> cir) {
		ServerPlayNetworkHandler self = (ServerPlayNetworkHandler)(Object)this;
		ServerPlayerEntity player = self.player;
		if (player != null && (GexpressPermissions.isHostOrDev(player)
				|| (PelicanManager.isVulture(player) && dev.doctor4t.wathe.game.GameFunctions.isPlayerAliveAndSurvival(player))
				|| PelicanManager.isStashed(player))) {
			cir.setReturnValue(false);
		}
	}
}
