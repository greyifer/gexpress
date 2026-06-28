package dev.mapselect.mixin;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.game.EndgameRulesManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameWorldComponent.class, remap = false)
public abstract class GexpressKillerFeaturesMixin {
	@Inject(method = "canUseKillerFeatures", at = @At("HEAD"), cancellable = true)
	private void gexpress$allowServerKillerRoles(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
		if (!(player instanceof ServerPlayerEntity)) return;
		try {
			GameWorldComponent game = (GameWorldComponent) (Object) this;
			if (!game.isRunning()) return;
			if (EndgameRulesManager.disablesKillerInstinct(player)) {
				cir.setReturnValue(false);
				return;
			}
			Role role = game.getRole(player);
			if (role != null && role.canUseKiller()) cir.setReturnValue(true);
		} catch (Throwable ignored) {
			// Wathe remains authoritative if this compatibility path cannot resolve the role.
		}
	}
}
