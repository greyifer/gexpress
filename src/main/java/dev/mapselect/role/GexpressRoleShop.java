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

	private GexpressRoleShop() {}

	public static List<ShopEntry> resolve(PlayerEntity player) {
		Role role = roleOf(player);
		if (role == null) return GameConstants.SHOP_ENTRIES;
		Identifier id = role.identifier();
		if (BOMB_SPECIALIST_ID.equals(id)) return bombSpecialistList();
		return GameConstants.SHOP_ENTRIES;
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

	private static Role roleOf(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return null;
		GameWorldComponent gwc = GameWorldComponent.KEY.getNullable(player.getWorld());
		return gwc == null ? null : gwc.getRole(player);
	}
}
