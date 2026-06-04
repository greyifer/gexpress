package dev.mapselect.mixin;

import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.role.harlequin.HarlequinManager;
import dev.mapselect.role.puppetmaster.PuppetmasterManager;
import dev.mapselect.role.skincrawler.SkincrawlerManager;
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
		Text disguiseName = disguiseNameFor(self);
		if (disguiseName != null) {
			cir.setReturnValue(disguiseName);
			return;
		}
		cir.setReturnValue(GexpressPermissions.displayName(self));
	}

	private static Text disguiseNameFor(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) return null;
		java.util.UUID replacement = HarlequinManager.replacementFor(player.getUuid());
		if (replacement == null) replacement = SkincrawlerManager.replacementFor(player.getUuid());
		if (replacement == null || replacement.equals(player.getUuid())) return null;
		ServerPlayerEntity replacementPlayer = player.getServer().getPlayerManager().getPlayer(replacement);
		return replacementPlayer == null ? null : GexpressPermissions.displayName(replacementPlayer);
	}
}
