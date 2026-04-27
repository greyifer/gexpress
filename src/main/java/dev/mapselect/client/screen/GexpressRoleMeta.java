package dev.mapselect.client.screen;

import cat.rezelyn.watheextended.client.screen.guidebook.RoleItemsRegistry;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;

import java.util.ArrayList;
import java.util.List;

public final class GexpressRoleMeta {
	private GexpressRoleMeta() {}

	public record ShopItem(String iconName, String nameKey, int price, boolean starting) {}

	private static final String BOMB_SPECIALIST_ID = MapSelect.MOD_ID + ":bomb_specialist";
	private static final String JUGGERNAUT_ID = MapSelect.MOD_ID + ":juggernaut";

	public static List<ShopItem> resolveShop(String roleId, boolean killerSided) {
		if (BOMB_SPECIALIST_ID.equals(roleId)) {
			return bombSpecialistShop();
		}
		if (JUGGERNAUT_ID.equals(roleId)) {
			return juggernautLoadout();
		}
		return fromWe(roleId, killerSided);
	}

	private static List<ShopItem> bombSpecialistShop() {
		List<ShopItem> out = new ArrayList<>();
		out.add(new ShopItem("c4", "item.gexpress.c4", GexpressConfig.getC4Price(), false));
		out.add(new ShopItem("grenade", "item.wathe.grenade", GexpressConfig.getGrenadePrice(), false));
		out.add(new ShopItem("firecracker", "item.wathe.firecracker", 10, false));
		out.add(new ShopItem("lockpick", "item.wathe.lockpick", 50, false));
		out.add(new ShopItem("crowbar", "item.wathe.crowbar", 25, false));
		out.add(new ShopItem("note", "item.wathe.note", 10, false));
		return out;
	}

	private static List<ShopItem> juggernautLoadout() {
		List<ShopItem> out = new ArrayList<>();
		out.add(new ShopItem("knife", "item.wathe.knife", 0, true));
		out.add(new ShopItem("revolver", "item.wathe.revolver", 0, true));
		return out;
	}

	private static List<ShopItem> fromWe(String roleId, boolean killerSided) {
		List<ShopItem> out = new ArrayList<>();
		try {
			List<RoleItemsRegistry.RoleItem> items = RoleItemsRegistry.getItemsForRole(roleId, killerSided);
			for (RoleItemsRegistry.RoleItem item : items) {
				out.add(new ShopItem(item.iconName(), item.nameKey(), item.price(), item.price() == 0));
			}
		} catch (Throwable t) {
			MapSelect.LOGGER.debug("Failed to query RoleItemsRegistry for {}: {}", roleId, t.toString());
		}
		return out;
	}
}
