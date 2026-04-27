package dev.mapselect.mixin;

import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.role.puppetmaster.PuppetmasterManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityDisplayNameMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void mapselect$hostDisplayName(CallbackInfoReturnable<Text> cir) {
		PlayerEntity self = (PlayerEntity)(Object)this;
		if (self instanceof ServerPlayerEntity serverPlayer) {
			Text puppetName = PuppetmasterManager.displayNameFor(serverPlayer);
			if (puppetName != null) {
				cir.setReturnValue(puppetName);
				return;
			}
		}
		if (!GexpressPermissions.hasBadge(self)) return;
		cir.setReturnValue(GexpressPermissions.displayName(self));
	}
}
