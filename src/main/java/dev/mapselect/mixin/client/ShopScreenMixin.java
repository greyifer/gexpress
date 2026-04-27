package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.mapselect.role.GexpressRoleShop;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Replaces the global killer shop list with the Bomb Specialist's custom six-slot loadout
 * when the viewing player is a Bomb Specialist. Targets the GETSTATIC of
 * GameConstants.SHOP_ENTRIES inside the screen's init (method_25426) — the sole read in that
 * method, used to build the widget grid.
 *
 * Pairs with ShopPurchaseMixin on the server side; both must resolve the same list or slot
 * indices will refer to different items on the two sides and purchases will get scrambled.
 */
@Mixin(value = LimitedInventoryScreen.class, remap = false)
public abstract class ShopScreenMixin {
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
