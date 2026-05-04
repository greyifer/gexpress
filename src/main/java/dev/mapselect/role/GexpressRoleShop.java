package dev.mapselect.role;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.registry.MapSelectItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Single source of truth for role-specific shop lists. The Wathe shop is one static list, so
 * both client display and server purchase handling must resolve the same list for each role.
 */
public final class GexpressRoleShop {
	private static final Identifier BOMB_SPECIALIST_ID = Identifier.of(MapSelect.MOD_ID, "bomb_specialist");
	private static final Identifier GODFATHER_ID = Identifier.of(MapSelect.MOD_ID, "godfather");
	private static final Identifier MAFIOSO_ID = Identifier.of(MapSelect.MOD_ID, "mafioso");
	private static final Identifier JANITOR_ID = Identifier.of(MapSelect.MOD_ID, "janitor");

	private GexpressRoleShop() {}

	public static List<ShopEntry> resolve(PlayerEntity player) {
		Role role = roleOf(player);
		if (role == null) return GameConstants.SHOP_ENTRIES;
		Identifier id = role.identifier();
		if (BOMB_SPECIALIST_ID.equals(id)) return bombSpecialistList();
		if (GODFATHER_ID.equals(id)) return godfatherList();
		if (MAFIOSO_ID.equals(id)) return mafiosoList();
		if (JANITOR_ID.equals(id)) return janitorList();
		return GameConstants.SHOP_ENTRIES;
	}

	public static boolean hasCustomShop(PlayerEntity player) {
		Role role = roleOf(player);
		if (role == null) return false;
		Identifier id = role.identifier();
		return BOMB_SPECIALIST_ID.equals(id)
			|| GODFATHER_ID.equals(id)
			|| MAFIOSO_ID.equals(id)
			|| JANITOR_ID.equals(id);
	}

	public static List<ShopEntry> bombSpecialistList() {
		return List.of(
			new ShopEntry(new ItemStack(MapSelectItems.C4), GexpressConfig.getC4Price(), ShopEntry.Type.WEAPON),
			new ShopEntry(WatheItems.GRENADE.getDefaultStack(), GexpressConfig.getGrenadePrice(), ShopEntry.Type.WEAPON),
			new ShopEntry(WatheItems.FIRECRACKER.getDefaultStack(), 10, ShopEntry.Type.WEAPON),
			new ShopEntry(WatheItems.LOCKPICK.getDefaultStack(), 50, ShopEntry.Type.TOOL),
			new ShopEntry(WatheItems.CROWBAR.getDefaultStack(), 25, ShopEntry.Type.TOOL),
			new ShopEntry(WatheItems.NOTE.getDefaultStack(), 10, ShopEntry.Type.TOOL)
		);
	}

	public static List<ShopEntry> godfatherList() {
		return List.of(
			customShopEntry(new ItemStack(MapSelectItems.BULLET), GexpressConfig.getGodfatherBulletPrice(), ShopEntry.Type.WEAPON)
		);
	}

	public static List<ShopEntry> mafiosoList() {
		return List.of(
			customShopEntry(WatheItems.KNIFE.getDefaultStack(), 200, ShopEntry.Type.WEAPON),
			customShopEntry(WatheItems.REVOLVER.getDefaultStack(), 350, ShopEntry.Type.WEAPON),
			customShopEntry(WatheItems.GRENADE.getDefaultStack(), GexpressConfig.getGrenadePrice(), ShopEntry.Type.WEAPON)
		);
	}

	public static List<ShopEntry> janitorList() {
		return List.of(
			customShopEntry(WatheItems.POISON_VIAL.getDefaultStack(), 100, ShopEntry.Type.POISON)
		);
	}

	private static ShopEntry customShopEntry(ItemStack stack, int price, ShopEntry.Type type) {
		return new ShopEntry(stack, price, type) {
			@Override
			public boolean onBuy(PlayerEntity player) {
				return ShopEntry.insertStackInFreeSlot(player, this.stack().copy());
			}
		};
	}

	private static Role roleOf(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return null;
		GameWorldComponent gwc = GameWorldComponent.KEY.getNullable(player.getWorld());
		return gwc == null ? null : gwc.getRole(player);
	}
}
