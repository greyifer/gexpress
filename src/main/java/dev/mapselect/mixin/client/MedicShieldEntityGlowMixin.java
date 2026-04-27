package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientMedicShieldState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MedicShieldEntityGlowMixin {
	@Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
	private void gexpress$medicShieldGlow(CallbackInfoReturnable<Boolean> cir) {
		if (ClientMedicShieldState.shouldGlow((Entity) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
	private void gexpress$medicShieldGlowColor(CallbackInfoReturnable<Integer> cir) {
		if (ClientMedicShieldState.shouldGlow((Entity) (Object) this)) {
			cir.setReturnValue(ClientMedicShieldState.SHIELD_COLOR);
		}
	}
}
