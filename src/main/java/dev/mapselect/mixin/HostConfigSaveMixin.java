package dev.mapselect.mixin;

import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "cat.rezelyn.watheextended.WatheExtended", remap = false)
public abstract class HostConfigSaveMixin {
	@Redirect(
		method = "lambda$registerNetworking$14",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayerEntity;hasPermissionLevel(I)Z",
			remap = true
		),
		require = 0
	)
	private static boolean mapselect$allowHost(ServerPlayerEntity player, int level) {
		return player.hasPermissionLevel(level) || GexpressPermissions.isHostOrDev(player);
	}
}
