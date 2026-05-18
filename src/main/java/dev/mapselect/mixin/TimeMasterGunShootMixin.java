package dev.mapselect.mixin;

import dev.doctor4t.wathe.util.GunShootPayload;
import dev.mapselect.role.mafia.MafiaManager;
import dev.mapselect.role.timemaster.TimeMasterManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunShootPayload.Receiver.class)
public abstract class TimeMasterGunShootMixin {
	@Inject(
		method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void gexpress$blockFrozenGunShot(GunShootPayload payload,
			ServerPlayNetworking.Context context, CallbackInfo ci) {
		if (TimeMasterManager.isFrozen(context.player()) || TimeMasterManager.isRewinding(context.player())) {
			ci.cancel();
			return;
		}
		if (!MafiaManager.beforeGunShot(context.player())) ci.cancel();
	}

	@Inject(
		method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V",
			ordinal = 1,
			shift = At.Shift.AFTER
		)
	)
	private void gexpress$recordTimeMasterRevolverShot(GunShootPayload payload,
			ServerPlayNetworking.Context context, CallbackInfo ci) {
		TimeMasterManager.recordWeaponEvent(context.player(), TimeMasterManager.WeaponEventType.REVOLVER_SHOT);
	}

	@Inject(
		method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
		at = @At("TAIL")
	)
	private void gexpress$applyMafiaGunRules(GunShootPayload payload,
			ServerPlayNetworking.Context context, CallbackInfo ci) {
		MafiaManager.afterGunShot(context.player());
	}
}
