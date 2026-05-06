package dev.mapselect.mixin;

import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.role.puppetmaster.PuppetmasterManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerListNameMixin {
	@Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
	private void mapselect$hostPlayerListName(CallbackInfoReturnable<Text> cir) {
		ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
		Text puppetName = PuppetmasterManager.displayNameFor(self);
		if (puppetName != null) {
			cir.setReturnValue(puppetName);
			return;
		}
		cir.setReturnValue(GexpressPermissions.displayName(self));
	}
}
