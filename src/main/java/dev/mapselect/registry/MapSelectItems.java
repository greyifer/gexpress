package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import dev.mapselect.item.C4DetonatorItem;
import dev.mapselect.item.C4Item;
import dev.mapselect.item.PliersItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class MapSelectItems {
	public static final C4Item C4 = new C4Item(new Item.Settings().maxCount(16));
	public static final C4DetonatorItem C4_DETONATOR = new C4DetonatorItem(new Item.Settings().maxCount(1));
	public static final PliersItem PLIERS = new PliersItem(new Item.Settings().maxCount(1));

	public static void register() {
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "c4"), C4);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "c4_detonator"), C4_DETONATOR);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "pliers"), PLIERS);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(C4));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(C4_DETONATOR));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(PLIERS));
	}

	private MapSelectItems() {}
}
