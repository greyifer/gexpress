package dev.mapselect.mixin.client;

import dev.mapselect.client.role.medic.ClientMedicShieldState;
import dev.mapselect.client.role.mafia.ClientMafiaState;
import dev.mapselect.client.role.snitch.ClientSnitchState;
import dev.mapselect.client.role.mafia.ClientJanitorState;
import dev.mapselect.client.role.guardian.ClientGuardianAngelState;
import dev.mapselect.client.role.bodyguard.ClientBodyguardState;
import dev.mapselect.client.role.timemaster.ClientTimeMasterFreezeState;
import dev.mapselect.client.role.tracker.ClientTrackerState;
import dev.mapselect.client.role.painter.ClientPainterState;
import dev.mapselect.client.ability.ClientAbilityTargetState;
import dev.mapselect.client.role.vengeful.ClientVengefulSpiritState;
import dev.mapselect.client.game.ClientSpectatorRoleRevealDelay;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
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
		if ((Object) this instanceof AbstractClientPlayerEntity player
				&& ClientSpectatorRoleRevealDelay.shouldGlow(player)) {
			cir.setReturnValue(true);
			return;
		}
		if (ClientMedicShieldState.shouldGlow((Entity) (Object) this)
				|| ClientSnitchState.shouldGlow((Entity) (Object) this)
				|| ClientJanitorState.shouldGlow((Entity) (Object) this)
				|| ((Object) this instanceof PlayerBodyEntity paintedBody && ClientPainterState.shouldGlow(paintedBody))
				|| ((Object) this instanceof PlayerBodyEntity targetBody && ClientAbilityTargetState.shouldGlow(targetBody))
				|| ((Object) this instanceof AbstractClientPlayerEntity player
					&& (ClientTimeMasterFreezeState.shouldGlow(player)
						|| ClientMafiaState.shouldGlow(player.getUuid())
						|| ClientGuardianAngelState.shouldGlow(player)
						|| ClientBodyguardState.shouldGlow(player)
						|| ClientPainterState.shouldGlow(player)
						|| ClientTrackerState.isTracked(player.getUuid())
						|| ClientVengefulSpiritState.shouldGlow(player)
						|| ClientAbilityTargetState.shouldGlow(player)
						|| ClientSpectatorRoleRevealDelay.shouldGlow(player)))) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
	private void gexpress$medicShieldGlowColor(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof AbstractClientPlayerEntity player
				&& ClientSpectatorRoleRevealDelay.shouldGlow(player)) {
			cir.setReturnValue(ClientSpectatorRoleRevealDelay.glowColor(player));
			return;
		}
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
		if ((Object) this instanceof PlayerBodyEntity body && ClientPainterState.shouldGlow(body)) {
			cir.setReturnValue(ClientPainterState.glowColor(body));
			return;
		}
		if ((Object) this instanceof PlayerBodyEntity body && ClientAbilityTargetState.shouldGlow(body)) {
			cir.setReturnValue(ClientAbilityTargetState.glowColor(body));
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
				&& ClientBodyguardState.shouldGlow(player)) {
			cir.setReturnValue(ClientBodyguardState.GLOW_COLOR);
			return;
		}
		if ((Object) this instanceof AbstractClientPlayerEntity player
				&& ClientPainterState.shouldGlow(player)) {
			cir.setReturnValue(ClientPainterState.glowColor(player));
			return;
		}
		if ((Object) this instanceof AbstractClientPlayerEntity player
				&& ClientTrackerState.isTracked(player.getUuid())) {
			cir.setReturnValue(0x3E9CFF);
			return;
		}
		if ((Object) this instanceof AbstractClientPlayerEntity player
				&& ClientVengefulSpiritState.shouldGlow(player)) {
			cir.setReturnValue(ClientVengefulSpiritState.KILLER_GLOW_COLOR);
			return;
		}
		if ((Object) this instanceof AbstractClientPlayerEntity player
				&& ClientAbilityTargetState.shouldGlow(player)) {
			cir.setReturnValue(ClientAbilityTargetState.glowColor(player));
			return;
		}
	}
}
