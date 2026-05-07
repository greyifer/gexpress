package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientMedicShieldState;
import dev.mapselect.client.ClientMafiaState;
import dev.mapselect.client.ClientSnitchState;
import dev.mapselect.client.ClientJanitorState;
import dev.mapselect.client.ClientGuardianAngelState;
import dev.mapselect.client.ClientTimeMasterFreezeState;
import dev.mapselect.client.ClientTrackerState;
import net.minecraft.entity.Entity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MedicShieldEntityGlowMixin {
	@Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
	private void gexpress$medicShieldGlow(CallbackInfoReturnable<Boolean> cir) {
		if (ClientMedicShieldState.shouldGlow((Entity) (Object) this)
				|| ClientSnitchState.shouldGlow((Entity) (Object) this)
				|| ClientJanitorState.shouldGlow((Entity) (Object) this)
				|| ((Object) this instanceof AbstractClientPlayerEntity player
					&& (ClientTimeMasterFreezeState.shouldGlow(player)
						|| ClientMafiaState.shouldGlow(player.getUuid())
						|| ClientGuardianAngelState.shouldGlow(player)
						|| ClientTrackerState.isTracked(player.getUuid())))) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
	private void gexpress$medicShieldGlowColor(CallbackInfoReturnable<Integer> cir) {
		if (ClientMedicShieldState.shouldGlow((Entity) (Object) this)) {
			cir.setReturnValue(ClientMedicShieldState.SHIELD_COLOR);
			return;
		}
		Integer snitchColor = ClientSnitchState.glowColor((Entity) (Object) this);
		if (snitchColor != null) {
			cir.setReturnValue(snitchColor);
			return;
		}
		if (ClientJanitorState.shouldGlow((Entity) (Object) this)) {
			cir.setReturnValue(ClientJanitorState.glowColor());
			return;
		}
		if ((Object) this instanceof AbstractClientPlayerEntity player
				&& ClientTimeMasterFreezeState.shouldGlow(player)) {
			cir.setReturnValue(ClientTimeMasterFreezeState.glowColor());
			return;
		}
		if ((Object) this instanceof AbstractClientPlayerEntity player
				&& ClientMafiaState.shouldGlow(player.getUuid())) {
			cir.setReturnValue(ClientMafiaState.glowColor());
			return;
		}
		if ((Object) this instanceof AbstractClientPlayerEntity player
				&& ClientGuardianAngelState.shouldGlow(player)) {
			cir.setReturnValue(ClientGuardianAngelState.SHIELD_COLOR);
			return;
		}
		if ((Object) this instanceof AbstractClientPlayerEntity player
				&& ClientTrackerState.isTracked(player.getUuid())) {
			cir.setReturnValue(0x3E9CFF);
		}
	}
}
