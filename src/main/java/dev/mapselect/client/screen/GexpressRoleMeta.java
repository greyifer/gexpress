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
	private static final String GODFATHER_ID = MapSelect.MOD_ID + ":godfather";
	private static final String MAFIOSO_ID = MapSelect.MOD_ID + ":mafioso";
	private static final String JANITOR_ID = MapSelect.MOD_ID + ":janitor";
	private static final String PICKPOCKET_ID = MapSelect.MOD_ID + ":pickpocket";
	private static final String BURGLAR_ID = MapSelect.MOD_ID + ":burglar";

	public static List<ShopItem> resolveShop(String roleId, boolean killerSided) {
		if (BOMB_SPECIALIST_ID.equals(roleId)) {
			return bombSpecialistShop();
		}
		if (JUGGERNAUT_ID.equals(roleId)) {
			return juggernautLoadout();
		}
		if (GODFATHER_ID.equals(roleId)) return godfatherShop();
		if (MAFIOSO_ID.equals(roleId)) return mafiosoShop();
		if (JANITOR_ID.equals(roleId)) return janitorShop();
		if (PICKPOCKET_ID.equals(roleId)) return pickpocketShop();
		if (BURGLAR_ID.equals(roleId)) return burglarShop();
		return fromWe(roleId, killerSided);
	}

	private static List<ShopItem> bombSpecialistShop() {
		List<ShopItem> out = new ArrayList<>();
		out.add(new ShopItem("c4", "item.gexpress.c4", GexpressConfig.getC4Price(), false));
		out.add(new ShopItem("grenade", "item.wathe.grenade", GexpressConfig.getGrenadePrice(), false));
		out.add(new ShopItem("firecracker", "item.wathe.firecracker", GexpressConfig.getBombSpecialistFirecrackerPrice(), false));
		out.add(new ShopItem("lockpick", "item.wathe.lockpick", GexpressConfig.getBombSpecialistLockpickPrice(), false));
		out.add(new ShopItem("crowbar", "item.wathe.crowbar", GexpressConfig.getBombSpecialistCrowbarPrice(), false));
		out.add(new ShopItem("note", "item.wathe.note", GexpressConfig.getMutedNotePrice(), false));
		return out;
	}

	private static List<ShopItem> juggernautLoadout() {
		List<ShopItem> out = new ArrayList<>();
		out.add(new ShopItem("knife", "item.wathe.knife", 0, true));
		out.add(new ShopItem("revolver", "item.wathe.revolver", 0, true));
		return out;
	}

	private static List<ShopItem> godfatherShop() {
		List<ShopItem> out = new ArrayList<>();
		out.add(new ShopItem("revolver", "item.wathe.revolver", 0, true));
		out.add(new ShopItem("bullet", "item.gexpress.bullet", GexpressConfig.getGodfatherBulletPrice(), false));
		return out;
	}

	private static List<ShopItem> mafiosoShop() {
		List<ShopItem> out = new ArrayList<>();
		out.add(new ShopItem("knife", "item.wathe.knife", GexpressConfig.getMafiosoKnifePrice(), false));
		out.add(new ShopItem("revolver", "item.wathe.revolver", GexpressConfig.getMafiosoRevolverPrice(), false));
		out.add(new ShopItem("grenade", "item.wathe.grenade", GexpressConfig.getGrenadePrice(), false));
		return out;
	}

	private static List<ShopItem> janitorShop() {
		List<ShopItem> out = new ArrayList<>();
		out.add(new ShopItem("poison_vial", "item.wathe.poison_vial", GexpressConfig.getJanitorPoisonVialPrice(), false));
		out.add(new ShopItem("scorpion", "item.wathe.scorpion", GexpressConfig.getJanitorScorpionPrice(), false));
		return out;
	}

	private static List<ShopItem> pickpocketShop() {
		List<ShopItem> out = new ArrayList<>();
		out.add(new ShopItem("note", "item.wathe.note", GexpressConfig.getMutedNotePrice(), false));
		return out;
	}

	private static List<ShopItem> burglarShop() {
		List<ShopItem> out = new ArrayList<>();
		out.add(new ShopItem("crowbar", "item.wathe.crowbar", GexpressConfig.getBurglarCrowbarPrice(), false));
		out.add(new ShopItem("lockpick", "item.wathe.lockpick", GexpressConfig.getBurglarLockpickPrice(), false));
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
