package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.StoreRenderer;
import dev.mapselect.role.GexpressRoleShop;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = StoreRenderer.class, remap = false)
public abstract class StoreRendererMixin {
	@Redirect(
		method = "renderHud",
		at = @At(
			value = "INVOKE",
			target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;canUseKillerFeatures(Lnet/minecraft/class_1657;)Z"
		),
		remap = false
	)
	private static boolean gexpress$showMoneyForCustomShopRoles(GameWorldComponent game, PlayerEntity player) {
		return game.canUseKillerFeatures(player) || GexpressRoleShop.showsMoneyHud(player);
	}
}
