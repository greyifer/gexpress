package dev.mapselect.registry;

import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.MapSelect;
import dev.mapselect.block.GreyiferPlushBlock;
import dev.mapselect.block.PebbleBlock;
import dev.mapselect.block.SandLayerBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class MapSelectBlocks {
	public static final Block SAND_LAYER = new SandLayerBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.PALE_YELLOW)
		.replaceable()
		.strength(0.5f)
		.sounds(BlockSoundGroup.SAND)
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block RED_SAND_LAYER = new SandLayerBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.ORANGE)
		.replaceable()
		.strength(0.5f)
		.sounds(BlockSoundGroup.SAND)
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block GREYIFER_PLUSH = new GreyiferPlushBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.BLACK)
		.strength(0.5f)
		.sounds(BlockSoundGroup.WOOL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block IWY_PLUSH = new GreyiferPlushBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.WHITE)
		.strength(0.5f)
		.sounds(BlockSoundGroup.WOOL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block FAKE_SUSPICIOUS_SAND = new Block(AbstractBlock.Settings.create()
		.mapColor(MapColor.PALE_YELLOW)
		.strength(0.25f)
		.sounds(BlockSoundGroup.SUSPICIOUS_SAND)
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block FAKE_SUSPICIOUS_GRAVEL = new Block(AbstractBlock.Settings.create()
		.mapColor(MapColor.STONE_GRAY)
		.strength(0.25f)
		.sounds(BlockSoundGroup.SUSPICIOUS_GRAVEL)
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block PEBBLE_BLOCK = new PebbleBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.STONE_GRAY)
		.strength(0.5f)
		.sounds(BlockSoundGroup.STONE)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final BlockItem SAND_LAYER_ITEM = new BlockItem(SAND_LAYER, new Item.Settings());
	public static final BlockItem RED_SAND_LAYER_ITEM = new BlockItem(RED_SAND_LAYER, new Item.Settings());
	public static final BlockItem GREYIFER_PLUSH_ITEM = new BlockItem(GREYIFER_PLUSH, new Item.Settings());
	public static final BlockItem IWY_PLUSH_ITEM = new BlockItem(IWY_PLUSH, new Item.Settings());
	public static final BlockItem FAKE_SUSPICIOUS_SAND_ITEM = new BlockItem(FAKE_SUSPICIOUS_SAND, new Item.Settings());
	public static final BlockItem FAKE_SUSPICIOUS_GRAVEL_ITEM = new BlockItem(FAKE_SUSPICIOUS_GRAVEL, new Item.Settings());
	public static final BlockItem PEBBLE_BLOCK_ITEM = new BlockItem(PEBBLE_BLOCK, new Item.Settings());

	public static void register() {
		Identifier sandId = Identifier.of(MapSelect.MOD_ID, "sand_layer");
		Identifier redSandId = Identifier.of(MapSelect.MOD_ID, "red_sand_layer");
		Identifier greyiferPlushId = Identifier.of(MapSelect.MOD_ID, "greyifer_plush");
		Identifier iwyPlushId = Identifier.of(MapSelect.MOD_ID, "iwy_plush");
		Identifier fakeSuspiciousSandId = Identifier.of(MapSelect.MOD_ID, "fake_suspicious_sand");
		Identifier fakeSuspiciousGravelId = Identifier.of(MapSelect.MOD_ID, "fake_suspicious_gravel");
		Identifier pebbleBlockId = Identifier.of(MapSelect.MOD_ID, "pebble_block");
		Registry.register(Registries.BLOCK, sandId, SAND_LAYER);
		Registry.register(Registries.BLOCK, redSandId, RED_SAND_LAYER);
		Registry.register(Registries.BLOCK, greyiferPlushId, GREYIFER_PLUSH);
		Registry.register(Registries.BLOCK, iwyPlushId, IWY_PLUSH);
		Registry.register(Registries.BLOCK, fakeSuspiciousSandId, FAKE_SUSPICIOUS_SAND);
		Registry.register(Registries.BLOCK, fakeSuspiciousGravelId, FAKE_SUSPICIOUS_GRAVEL);
		Registry.register(Registries.BLOCK, pebbleBlockId, PEBBLE_BLOCK);
		Registry.register(Registries.ITEM, sandId, SAND_LAYER_ITEM);
		Registry.register(Registries.ITEM, redSandId, RED_SAND_LAYER_ITEM);
		Registry.register(Registries.ITEM, greyiferPlushId, GREYIFER_PLUSH_ITEM);
		Registry.register(Registries.ITEM, iwyPlushId, IWY_PLUSH_ITEM);
		Registry.register(Registries.ITEM, fakeSuspiciousSandId, FAKE_SUSPICIOUS_SAND_ITEM);
		Registry.register(Registries.ITEM, fakeSuspiciousGravelId, FAKE_SUSPICIOUS_GRAVEL_ITEM);
		Registry.register(Registries.ITEM, pebbleBlockId, PEBBLE_BLOCK_ITEM);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
			entries.add(SAND_LAYER_ITEM);
			entries.add(RED_SAND_LAYER_ITEM);
			entries.add(FAKE_SUSPICIOUS_SAND_ITEM);
			entries.add(FAKE_SUSPICIOUS_GRAVEL_ITEM);
			entries.add(PEBBLE_BLOCK_ITEM);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(GREYIFER_PLUSH_ITEM);
			entries.add(IWY_PLUSH_ITEM);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SEARCH).register(entries -> {
			entries.add(GREYIFER_PLUSH_ITEM);
			entries.add(IWY_PLUSH_ITEM);
			entries.add(FAKE_SUSPICIOUS_SAND_ITEM);
			entries.add(FAKE_SUSPICIOUS_GRAVEL_ITEM);
			entries.add(PEBBLE_BLOCK_ITEM);
		});
		ItemGroupEvents.modifyEntriesEvent(WatheItems.DECORATION_GROUP).register(entries -> {
			entries.add(GREYIFER_PLUSH_ITEM);
			entries.add(IWY_PLUSH_ITEM);
			entries.add(FAKE_SUSPICIOUS_SAND_ITEM);
			entries.add(FAKE_SUSPICIOUS_GRAVEL_ITEM);
			entries.add(PEBBLE_BLOCK_ITEM);
		});
	}

	private MapSelectBlocks() {}
}
