package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.client.ClientAmnesiaState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameWorldComponent.class, remap = false)
public abstract class AmnesiaKillerIdentityMixin {
	@Inject(method = "canUseKillerFeatures", at = @At("HEAD"), cancellable = true)
	private void gexpress$hideOtherKillersInAmnesia(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
		if (ClientAmnesiaState.shouldHideKillerIdentity(player)) {
			cir.setReturnValue(false);
		}
	}
}
