package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.mapselect.role.GexpressRoleShop;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Replaces Wathe's static killer shop entries with the active role's custom list.
 * The server-side purchase mixin must resolve the same list, or slot indices will drift.
 */
@Mixin(value = LimitedInventoryScreen.class, remap = false)
public abstract class ShopScreenMixin {
	@Dynamic("LimitedInventoryScreen is compiled with intermediary method names in the Wathe jar.")
	@Redirect(
		method = "method_25426",
		at = @At(
			value = "INVOKE",
			target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;canUseKillerFeatures(Lnet/minecraft/class_1657;)Z"
		),
		remap = false
	)
	private boolean gexpress$customShopRolesCanSeeShop(GameWorldComponent game, PlayerEntity player) {
		return game.canUseKillerFeatures(player) || GexpressRoleShop.hasCustomShop(player);
	}

	@Dynamic("LimitedInventoryScreen is compiled with intermediary method names in the Wathe jar.")
	@Redirect(
		method = "method_25426",
		at = @At(
			value = "FIELD",
			target = "Ldev/doctor4t/wathe/game/GameConstants;SHOP_ENTRIES:Ljava/util/List;",
			opcode = Opcodes.GETSTATIC
		),
		remap = false
	)
	private List<ShopEntry> gexpress$bombSpecialistShop() {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		return GexpressRoleShop.resolve(player);
	}
}
