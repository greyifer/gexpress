package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class MapSelectItemGroups {
	public static final ItemGroup SPECIAL_BLOCKS = Registry.register(
		Registries.ITEM_GROUP,
		Identifier.of(MapSelect.MOD_ID, "special_blocks"),
		FabricItemGroup.builder()
			.icon(() -> new ItemStack(MapSelectBlocks.GREYIFER_PLUSH_ITEM))
			.displayName(Text.translatable("itemGroup.gexpress.special_blocks"))
			.entries((context, entries) -> {
				int count = 0;
				count += MapSelectItems.addCreativeSection(entries, MapSelectItems.CREATIVE_DECORATION_BLOCKS_HEADER);
				count += MapSelectBlocks.addDecorationBlockItems(entries);
				count += MapSelectItems.padCreativeRow(entries, count);
				count += MapSelectItems.addCreativeSection(entries, MapSelectItems.CREATIVE_ENVIRONMENTAL_BLOCKS_HEADER);
				count += MapSelectBlocks.addEnvironmentalBlockItems(entries);
				count += MapSelectItems.padCreativeRow(entries, count);
				count += MapSelectItems.addCreativeSection(entries, MapSelectItems.CREATIVE_BUILDING_BLOCKS_HEADER);
				count += MapSelectBlocks.addBuildingBlockItems(entries);
				count += MapSelectItems.padCreativeRow(entries, count);
				count += MapSelectItems.addCreativeSection(entries, MapSelectItems.CREATIVE_ITEMS_HEADER);
				MapSelectItems.addSpecialItems(entries);
			})
			.build()
	);

	public static void register() {
		// Static initializer registers the group.
	}

	private MapSelectItemGroups() {}
}
