package dev.mapselect.mixin;

import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.role.puppetmaster.PuppetmasterManager;
import dev.mapselect.role.skincrawler.SkincrawlerManager;
import dev.mapselect.role.trickster.TricksterManager;
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
			Text disguiseName = disguiseNameFor(serverPlayer);
			if (disguiseName != null) {
				cir.setReturnValue(disguiseName);
				return;
			}
		}
		if (!GexpressPermissions.hasBadge(self)) return;
		cir.setReturnValue(GexpressPermissions.displayName(self));
	}

	private static Text disguiseNameFor(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) return null;
		java.util.UUID replacement = TricksterManager.replacementFor(player.getUuid());
		if (replacement == null) replacement = SkincrawlerManager.replacementFor(player.getUuid());
		if (replacement == null || replacement.equals(player.getUuid())) return null;
		ServerPlayerEntity replacementPlayer = player.getServer().getPlayerManager().getPlayer(replacement);
		return replacementPlayer == null ? null : Text.literal(replacementPlayer.getName().getString());
	}
}
