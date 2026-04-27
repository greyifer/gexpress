package dev.mapselect.mixin;

import dev.doctor4t.wathe.Wathe;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Wathe.class)
public abstract class WatheSupporterMixin {
	@Inject(method = "isSupporter", at = @At("HEAD"), cancellable = true, remap = false)
	private static void mapselect$opBypass(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
		if (GexpressPermissions.bypassesSupporterGates(player)) {
			cir.setReturnValue(Boolean.TRUE);
		}
	}
}
