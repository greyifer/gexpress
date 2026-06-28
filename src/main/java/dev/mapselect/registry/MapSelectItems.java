package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import dev.mapselect.item.BulletItem;
import dev.mapselect.item.C4DetonatorItem;
import dev.mapselect.item.C4Item;
import dev.mapselect.item.CreativeSectionItem;
import dev.mapselect.item.GexpressCaseItem;
import dev.mapselect.item.PliersItem;
import dev.mapselect.item.TutorialWeaponItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class MapSelectItems {
	public static final C4Item C4 = new C4Item(new Item.Settings().maxCount(16));
	public static final C4DetonatorItem C4_DETONATOR = new C4DetonatorItem(new Item.Settings().maxCount(1));
	public static final PliersItem PLIERS = new PliersItem(new Item.Settings().maxCount(1));
	public static final BulletItem BULLET = new BulletItem(new Item.Settings().maxCount(3));
	public static final Item SPY_BUG = new Item(new Item.Settings().maxCount(1));
	public static final TutorialWeaponItem TUTORIAL_REVOLVER = new TutorialWeaponItem(
		TutorialWeaponItem.Kind.REVOLVER, new Item.Settings().maxCount(1));
	public static final TutorialWeaponItem TUTORIAL_KNIFE = new TutorialWeaponItem(
		TutorialWeaponItem.Kind.KNIFE, new Item.Settings().maxCount(1));
	public static final GexpressCaseItem GEXPRESS_CASE = new GexpressCaseItem(new Item.Settings().maxCount(64));
	public static final Item CREATIVE_DECORATION_BLOCKS_HEADER = new CreativeSectionItem(new Item.Settings().maxCount(1));
	public static final Item CREATIVE_ENVIRONMENTAL_BLOCKS_HEADER = new CreativeSectionItem(new Item.Settings().maxCount(1));
	public static final Item CREATIVE_BUILDING_BLOCKS_HEADER = new CreativeSectionItem(new Item.Settings().maxCount(1));
	public static final Item CREATIVE_ITEMS_HEADER = new CreativeSectionItem(new Item.Settings().maxCount(1));
	public static final RegistryKey<JukeboxSong> DERAILED_JUKEBOX_SONG = jukeboxSongKey("derailed");
	public static final RegistryKey<JukeboxSong> AERISTHEME_JUKEBOX_SONG = jukeboxSongKey("aeristheme");
	public static final Item MUSIC_DISC_DERAILED = new Item(new Item.Settings().maxCount(1)
		.rarity(Rarity.RARE)
		.jukeboxPlayable(DERAILED_JUKEBOX_SONG));
	public static final Item MUSIC_DISC_AERISTHEME = new Item(new Item.Settings().maxCount(1)
		.rarity(Rarity.RARE)
		.jukeboxPlayable(AERISTHEME_JUKEBOX_SONG));

	public static void register() {
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "c4"), C4);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "c4_detonator"), C4_DETONATOR);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "pliers"), PLIERS);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "bullet"), BULLET);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "spy_bug"), SPY_BUG);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "tutorial_revolver"), TUTORIAL_REVOLVER);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "tutorial_knife"), TUTORIAL_KNIFE);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "gexpress_case"), GEXPRESS_CASE);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "music_disc_derailed"), MUSIC_DISC_DERAILED);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "music_disc_aeristheme"), MUSIC_DISC_AERISTHEME);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "creative_decoration_blocks_header"), CREATIVE_DECORATION_BLOCKS_HEADER);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "creative_environmental_blocks_header"), CREATIVE_ENVIRONMENTAL_BLOCKS_HEADER);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "creative_building_blocks_header"), CREATIVE_BUILDING_BLOCKS_HEADER);
		Registry.register(Registries.ITEM, Identifier.of(MapSelect.MOD_ID, "creative_items_header"), CREATIVE_ITEMS_HEADER);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(C4));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(C4_DETONATOR));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(BULLET));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(PLIERS));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(MUSIC_DISC_DERAILED);
			entries.add(MUSIC_DISC_AERISTHEME);
		});
	}

	public static int addSpecialItems(FabricItemGroupEntries entries) {
		entries.add(C4);
		entries.add(C4_DETONATOR);
		entries.add(PLIERS);
		entries.add(BULLET);
		entries.add(SPY_BUG);
		entries.add(MUSIC_DISC_DERAILED);
		entries.add(MUSIC_DISC_AERISTHEME);
		return 7;
	}

	public static int addSpecialItems(ItemGroup.Entries entries) {
		entries.add(C4);
		entries.add(C4_DETONATOR);
		entries.add(PLIERS);
		entries.add(BULLET);
		entries.add(SPY_BUG);
		entries.add(MUSIC_DISC_DERAILED);
		entries.add(MUSIC_DISC_AERISTHEME);
		return 7;
	}

	public static int addCreativeSection(ItemGroup.Entries entries, Item sectionItem) {
		String marker = markerForSection(sectionItem);
		return addCreativeSection(entries, sectionItem, marker);
	}

	public static int addCreativeSection(ItemGroup.Entries entries, Item sectionItem, String marker) {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = new ItemStack(sectionItem);
			stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(marker + "_" + i));
			entries.add(stack, ItemGroup.StackVisibility.PARENT_TAB_ONLY);
		}
		return 9;
	}

	public static int padCreativeRow(ItemGroup.Entries entries, int currentCount) {
		int padding = Math.floorMod(-currentCount, 9);
		for (int i = 0; i < padding; i++) {
			ItemStack stack = new ItemStack(CREATIVE_DECORATION_BLOCKS_HEADER);
			stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("gexpress_section_padding_" + currentCount + "_" + i));
			entries.add(stack, ItemGroup.StackVisibility.PARENT_TAB_ONLY);
		}
		return padding;
	}

	private static String markerForSection(Item sectionItem) {
		if (sectionItem == CREATIVE_DECORATION_BLOCKS_HEADER) return "gexpress_section_decoration_blocks";
		if (sectionItem == CREATIVE_ENVIRONMENTAL_BLOCKS_HEADER) return "gexpress_section_environmental_blocks";
		if (sectionItem == CREATIVE_BUILDING_BLOCKS_HEADER) return "gexpress_section_building_blocks";
		return "gexpress_section_items";
	}

	private static RegistryKey<JukeboxSong> jukeboxSongKey(String path) {
		return RegistryKey.of(RegistryKeys.JUKEBOX_SONG, Identifier.of(MapSelect.MOD_ID, path));
	}

	private MapSelectItems() {}
}
