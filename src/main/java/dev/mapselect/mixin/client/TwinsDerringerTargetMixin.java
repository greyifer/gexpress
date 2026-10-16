package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.item.DerringerItem;
import dev.mapselect.role.twins.TwinWeaponTargeting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DerringerItem.class)
public abstract class TwinsDerringerTargetMixin {
	@Inject(method = "getGunTarget", at = @At("HEAD"), cancellable = true)
	private static void gexpress$targetTwinBody(PlayerEntity user, CallbackInfoReturnable<HitResult> cir) {
		cir.setReturnValue(TwinWeaponTargeting.findTarget(user, 7.0F));
	}
}
