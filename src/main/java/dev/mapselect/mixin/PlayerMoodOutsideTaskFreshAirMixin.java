package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.mapselect.task.FreshAirAreaManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerMoodComponent.OutsideTask.class, remap = false)
public abstract class PlayerMoodOutsideTaskFreshAirMixin {
	@Shadow
	private int timer;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void gexpress$tickFreshAirArea(PlayerEntity player, CallbackInfo ci) {
		if (timer > 0 && FreshAirAreaManager.countsAsFreshAir(player)) {
			timer--;
			ci.cancel();
		}
	}
}
