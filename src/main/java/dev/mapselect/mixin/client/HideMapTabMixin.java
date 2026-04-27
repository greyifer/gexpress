package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.screen.WatheOptionsScreen;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.mapselect.host.HostComponent;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WatheOptionsScreen.class)
public abstract class HideMapTabMixin {
	@Redirect(
		method = "create",
		at = @At(
			value = "INVOKE",
			target = "Ldev/isxander/yacl3/api/YetAnotherConfigLib$Builder;category(Ldev/isxander/yacl3/api/ConfigCategory;)Ldev/isxander/yacl3/api/YetAnotherConfigLib$Builder;",
			ordinal = 2,
			remap = false
		),
		remap = false,
		require = 0
	)
	private static YetAnotherConfigLib.Builder mapselect$skipMapTab(
		YetAnotherConfigLib.Builder builder,
		ConfigCategory category
	) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc != null && mc.player != null
			&& !mc.player.hasPermissionLevel(2)
			&& !GexpressPermissions.isDev(mc.player)
			&& HostComponent.isHost(mc.player)) {
			return builder;
		}
		return builder.category(category);
	}
}
