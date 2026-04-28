package dev.mapselect.mixin;

import dev.doctor4t.wathe.util.KnifeStabPayload;
import dev.mapselect.role.timemaster.TimeMasterManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KnifeStabPayload.Receiver.class)
public abstract class TimeMasterKnifeStabMixin {
	@Inject(
		method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V",
			shift = At.Shift.AFTER
		)
	)
	private void gexpress$recordTimeMasterKnifeStab(KnifeStabPayload payload,
			ServerPlayNetworking.Context context, CallbackInfo ci) {
		TimeMasterManager.recordWeaponEvent(context.player(), TimeMasterManager.WeaponEventType.KNIFE_STAB);
	}
}
