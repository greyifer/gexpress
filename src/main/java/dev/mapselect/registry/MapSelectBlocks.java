package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import dev.mapselect.block.CabinetBlock;
import dev.mapselect.block.CoinBarrierBlock;
import dev.mapselect.block.FloatingTextBlock;
import dev.mapselect.block.FusedOrnamentBlock;
import dev.mapselect.block.GexpressStairsBlock;
import dev.mapselect.block.GoldDrinkTrayBlock;
import dev.mapselect.block.GoldFoodPlatterBlock;
import dev.mapselect.block.GoldLedgeBlock;
import dev.mapselect.block.GreyiferPlushBlock;
import dev.mapselect.block.PebbleBlock;
import dev.mapselect.block.SandLayerBlock;
import dev.doctor4t.wathe.block.PanelBlock;
import dev.mapselect.item.FusedLedgeItem;
import dev.mapselect.item.FusedOrnamentItem;
import dev.mapselect.item.RedRibbonItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

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
		.pistonBehavior(PistonBehavior.DESTROY), () -> MapSelectSounds.IWY_PLUSH_HONK);

	public static final Block LUX_PLUSH = new GreyiferPlushBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.BLACK)
		.strength(0.5f)
		.sounds(BlockSoundGroup.WOOL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY), () -> MapSelectSounds.LUX_PLUSH_HONK);

	public static final Block WTFJIMJIM_PLUSH = new GreyiferPlushBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.BLACK)
		.strength(0.5f)
		.sounds(BlockSoundGroup.WOOL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY), () -> MapSelectSounds.WTFJIMJIM_PLUSH_HONK);

	public static final Block PIZZA_PLUSH = new GreyiferPlushBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.BLACK)
		.strength(0.5f)
		.sounds(BlockSoundGroup.WOOL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY), () -> MapSelectSounds.PIZZA_PLUSH_HONK);

	public static final Block JEMSEA_PLUSH = new GreyiferPlushBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.BLACK)
		.strength(0.5f)
		.sounds(BlockSoundGroup.WOOL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block PARROTMARROW_PLUSH = new GreyiferPlushBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.BLACK)
		.strength(0.5f)
		.sounds(BlockSoundGroup.WOOL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block EVIEEVEEE_PLUSH = new GreyiferPlushBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.BLACK)
		.strength(0.5f)
		.sounds(BlockSoundGroup.WOOL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block ASTRONOMIKYU_PLUSH = new GreyiferPlushBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.BLACK)
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

	public static final Block COIN_BARRIER = new CoinBarrierBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.BLACK)
		.strength(3.0f, 1200.0f)
		.sounds(BlockSoundGroup.WOOL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.BLOCK));

	public static final Block FLOATING_TEXT = new FloatingTextBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.CLEAR)
		.strength(1.0f)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block OLIVE_PLANKS = new Block(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS)
		.mapColor(MapColor.DARK_GREEN)
		.strength(2.0f, 3.0f)
		.sounds(BlockSoundGroup.WOOD));
	public static final Block OLIVE_PLANKS_STAIRS = stairs(OLIVE_PLANKS);
	public static final Block OLIVE_PLANKS_SLAB = slab(OLIVE_PLANKS);
	public static final Block OLIVE_PLANKS_PANEL = panel(OLIVE_PLANKS);

	public static final Block SMOOTH_OLIVE = new Block(oliveSettings());
	public static final Block SMOOTH_OLIVE_STAIRS = stairs(SMOOTH_OLIVE);
	public static final Block SMOOTH_OLIVE_SLAB = slab(SMOOTH_OLIVE);
	public static final Block SMOOTH_OLIVE_PANEL = panel(SMOOTH_OLIVE);

	public static final Block OLIVE_HERRINGBONE = new Block(oliveSettings());
	public static final Block OLIVE_HERRINGBONE_STAIRS = stairs(OLIVE_HERRINGBONE);
	public static final Block OLIVE_HERRINGBONE_SLAB = slab(OLIVE_HERRINGBONE);
	public static final Block OLIVE_HERRINGBONE_PANEL = panel(OLIVE_HERRINGBONE);

	public static final Block PLUM_PLANKS = new Block(plumSettings());
	public static final Block PLUM_PLANKS_STAIRS = stairs(PLUM_PLANKS);
	public static final Block PLUM_PLANKS_SLAB = slab(PLUM_PLANKS);
	public static final Block PLUM_PLANKS_PANEL = panel(PLUM_PLANKS);

	public static final Block SMOOTH_PLUM = new Block(plumSettings());
	public static final Block SMOOTH_PLUM_STAIRS = stairs(SMOOTH_PLUM);
	public static final Block SMOOTH_PLUM_SLAB = slab(SMOOTH_PLUM);
	public static final Block SMOOTH_PLUM_PANEL = panel(SMOOTH_PLUM);

	public static final Block PLUM_HERRINGBONE = new Block(plumSettings());
	public static final Block PLUM_HERRINGBONE_STAIRS = stairs(PLUM_HERRINGBONE);
	public static final Block PLUM_HERRINGBONE_SLAB = slab(PLUM_HERRINGBONE);
	public static final Block PLUM_HERRINGBONE_PANEL = panel(PLUM_HERRINGBONE);

	public static final Block ASH_PLANKS = new Block(ashSettings());
	public static final Block ASH_PLANKS_STAIRS = stairs(ASH_PLANKS);
	public static final Block ASH_PLANKS_SLAB = slab(ASH_PLANKS);
	public static final Block ASH_PLANKS_PANEL = panel(ASH_PLANKS);

	public static final Block SMOOTH_ASH = new Block(ashSettings());
	public static final Block SMOOTH_ASH_STAIRS = stairs(SMOOTH_ASH);
	public static final Block SMOOTH_ASH_SLAB = slab(SMOOTH_ASH);
	public static final Block SMOOTH_ASH_PANEL = panel(SMOOTH_ASH);

	public static final Block ASH_HERRINGBONE = new Block(ashSettings());
	public static final Block ASH_HERRINGBONE_STAIRS = stairs(ASH_HERRINGBONE);
	public static final Block ASH_HERRINGBONE_SLAB = slab(ASH_HERRINGBONE);
	public static final Block ASH_HERRINGBONE_PANEL = panel(ASH_HERRINGBONE);

	public static final Block GOLD_FOOD_PLATTER = new GoldFoodPlatterBlock(AbstractBlock.Settings.copy(Blocks.GOLD_BLOCK)
		.strength(0.5f)
		.sounds(BlockSoundGroup.METAL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block GOLD_DRINK_TRAY = new GoldDrinkTrayBlock(AbstractBlock.Settings.copy(Blocks.GOLD_BLOCK)
		.strength(0.5f)
		.sounds(BlockSoundGroup.METAL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY));

	public static final Block GOLD_LEDGE = new GoldLedgeBlock(AbstractBlock.Settings.copy(Blocks.GOLD_BLOCK)
		.strength(0.5f)
		.sounds(BlockSoundGroup.METAL)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.DESTROY));
	public static final Block ANTHRACITE_LEDGE = ledge(MapColor.BLACK);
	public static final Block KHAKI_LEDGE = ledge(MapColor.PALE_YELLOW);
	public static final Block MAROON_LEDGE = ledge(MapColor.DARK_RED);
	public static final Block MUNTZ_LEDGE = ledge(MapColor.GOLD);
	public static final Block NAVY_LEDGE = ledge(MapColor.BLUE);

	public static final Block FUSED_ORNAMENTED_BLOCK = new FusedOrnamentBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.GOLD)
		.strength(1.0f)
		.sounds(BlockSoundGroup.COPPER)
		.nonOpaque()
		.pistonBehavior(PistonBehavior.BLOCK));

	public static final Block ACACIA_CABINET = cabinet(MapColor.ORANGE);
	public static final Block BIRCH_CABINET = cabinet(MapColor.OAK_TAN);
	public static final Block CHERRY_CABINET = cabinet(MapColor.DIRT_BROWN);
	public static final Block DARK_OAK_CABINET = cabinet(MapColor.BROWN);
	public static final Block JUNGLE_CABINET = cabinet(MapColor.DIRT_BROWN);
	public static final Block MANGROVE_CABINET = cabinet(MapColor.TERRACOTTA_BROWN);
	public static final Block OAK_CABINET = cabinet(MapColor.OAK_TAN);
	public static final Block SPRUCE_CABINET = cabinet(MapColor.SPRUCE_BROWN);

	public static final BlockItem SAND_LAYER_ITEM = new BlockItem(SAND_LAYER, new Item.Settings());
	public static final BlockItem RED_SAND_LAYER_ITEM = new BlockItem(RED_SAND_LAYER, new Item.Settings());
	public static final BlockItem GREYIFER_PLUSH_ITEM = new BlockItem(GREYIFER_PLUSH, new Item.Settings());
	public static final BlockItem IWY_PLUSH_ITEM = new BlockItem(IWY_PLUSH, new Item.Settings());
	public static final BlockItem LUX_PLUSH_ITEM = new BlockItem(LUX_PLUSH, new Item.Settings());
	public static final BlockItem WTFJIMJIM_PLUSH_ITEM = new BlockItem(WTFJIMJIM_PLUSH, new Item.Settings());
	public static final BlockItem PIZZA_PLUSH_ITEM = new BlockItem(PIZZA_PLUSH, new Item.Settings());
	public static final BlockItem JEMSEA_PLUSH_ITEM = new BlockItem(JEMSEA_PLUSH, new Item.Settings());
	public static final BlockItem PARROTMARROW_PLUSH_ITEM = new BlockItem(PARROTMARROW_PLUSH, new Item.Settings());
	public static final BlockItem EVIEEVEEE_PLUSH_ITEM = new BlockItem(EVIEEVEEE_PLUSH, new Item.Settings());
	public static final BlockItem ASTRONOMIKYU_PLUSH_ITEM = new BlockItem(ASTRONOMIKYU_PLUSH, new Item.Settings());
	public static final BlockItem FAKE_SUSPICIOUS_SAND_ITEM = new BlockItem(FAKE_SUSPICIOUS_SAND, new Item.Settings());
	public static final BlockItem FAKE_SUSPICIOUS_GRAVEL_ITEM = new BlockItem(FAKE_SUSPICIOUS_GRAVEL, new Item.Settings());
	public static final BlockItem PEBBLE_BLOCK_ITEM = new BlockItem(PEBBLE_BLOCK, new Item.Settings());
	public static final Item COIN_BARRIER_ITEM = new RedRibbonItem(new Item.Settings().maxCount(1));
	public static final BlockItem FLOATING_TEXT_ITEM = new BlockItem(FLOATING_TEXT, new Item.Settings());
	public static final BlockItem OLIVE_PLANKS_ITEM = new BlockItem(OLIVE_PLANKS, new Item.Settings());
	public static final BlockItem OLIVE_PLANKS_STAIRS_ITEM = new BlockItem(OLIVE_PLANKS_STAIRS, new Item.Settings());
	public static final BlockItem OLIVE_PLANKS_SLAB_ITEM = new BlockItem(OLIVE_PLANKS_SLAB, new Item.Settings());
	public static final BlockItem OLIVE_PLANKS_PANEL_ITEM = new BlockItem(OLIVE_PLANKS_PANEL, new Item.Settings());
	public static final BlockItem SMOOTH_OLIVE_ITEM = new BlockItem(SMOOTH_OLIVE, new Item.Settings());
	public static final BlockItem SMOOTH_OLIVE_STAIRS_ITEM = new BlockItem(SMOOTH_OLIVE_STAIRS, new Item.Settings());
	public static final BlockItem SMOOTH_OLIVE_SLAB_ITEM = new BlockItem(SMOOTH_OLIVE_SLAB, new Item.Settings());
	public static final BlockItem SMOOTH_OLIVE_PANEL_ITEM = new BlockItem(SMOOTH_OLIVE_PANEL, new Item.Settings());
	public static final BlockItem OLIVE_HERRINGBONE_ITEM = new BlockItem(OLIVE_HERRINGBONE, new Item.Settings());
	public static final BlockItem OLIVE_HERRINGBONE_STAIRS_ITEM = new BlockItem(OLIVE_HERRINGBONE_STAIRS, new Item.Settings());
	public static final BlockItem OLIVE_HERRINGBONE_SLAB_ITEM = new BlockItem(OLIVE_HERRINGBONE_SLAB, new Item.Settings());
	public static final BlockItem OLIVE_HERRINGBONE_PANEL_ITEM = new BlockItem(OLIVE_HERRINGBONE_PANEL, new Item.Settings());
	public static final BlockItem PLUM_PLANKS_ITEM = new BlockItem(PLUM_PLANKS, new Item.Settings());
	public static final BlockItem PLUM_PLANKS_STAIRS_ITEM = new BlockItem(PLUM_PLANKS_STAIRS, new Item.Settings());
	public static final BlockItem PLUM_PLANKS_SLAB_ITEM = new BlockItem(PLUM_PLANKS_SLAB, new Item.Settings());
	public static final BlockItem PLUM_PLANKS_PANEL_ITEM = new BlockItem(PLUM_PLANKS_PANEL, new Item.Settings());
	public static final BlockItem SMOOTH_PLUM_ITEM = new BlockItem(SMOOTH_PLUM, new Item.Settings());
	public static final BlockItem SMOOTH_PLUM_STAIRS_ITEM = new BlockItem(SMOOTH_PLUM_STAIRS, new Item.Settings());
	public static final BlockItem SMOOTH_PLUM_SLAB_ITEM = new BlockItem(SMOOTH_PLUM_SLAB, new Item.Settings());
	public static final BlockItem SMOOTH_PLUM_PANEL_ITEM = new BlockItem(SMOOTH_PLUM_PANEL, new Item.Settings());
	public static final BlockItem PLUM_HERRINGBONE_ITEM = new BlockItem(PLUM_HERRINGBONE, new Item.Settings());
	public static final BlockItem PLUM_HERRINGBONE_STAIRS_ITEM = new BlockItem(PLUM_HERRINGBONE_STAIRS, new Item.Settings());
	public static final BlockItem PLUM_HERRINGBONE_SLAB_ITEM = new BlockItem(PLUM_HERRINGBONE_SLAB, new Item.Settings());
	public static final BlockItem PLUM_HERRINGBONE_PANEL_ITEM = new BlockItem(PLUM_HERRINGBONE_PANEL, new Item.Settings());
	public static final BlockItem ASH_PLANKS_ITEM = new BlockItem(ASH_PLANKS, new Item.Settings());
	public static final BlockItem ASH_PLANKS_STAIRS_ITEM = new BlockItem(ASH_PLANKS_STAIRS, new Item.Settings());
	public static final BlockItem ASH_PLANKS_SLAB_ITEM = new BlockItem(ASH_PLANKS_SLAB, new Item.Settings());
	public static final BlockItem ASH_PLANKS_PANEL_ITEM = new BlockItem(ASH_PLANKS_PANEL, new Item.Settings());
	public static final BlockItem SMOOTH_ASH_ITEM = new BlockItem(SMOOTH_ASH, new Item.Settings());
	public static final BlockItem SMOOTH_ASH_STAIRS_ITEM = new BlockItem(SMOOTH_ASH_STAIRS, new Item.Settings());
	public static final BlockItem SMOOTH_ASH_SLAB_ITEM = new BlockItem(SMOOTH_ASH_SLAB, new Item.Settings());
	public static final BlockItem SMOOTH_ASH_PANEL_ITEM = new BlockItem(SMOOTH_ASH_PANEL, new Item.Settings());
	public static final BlockItem ASH_HERRINGBONE_ITEM = new BlockItem(ASH_HERRINGBONE, new Item.Settings());
	public static final BlockItem ASH_HERRINGBONE_STAIRS_ITEM = new BlockItem(ASH_HERRINGBONE_STAIRS, new Item.Settings());
	public static final BlockItem ASH_HERRINGBONE_SLAB_ITEM = new BlockItem(ASH_HERRINGBONE_SLAB, new Item.Settings());
	public static final BlockItem ASH_HERRINGBONE_PANEL_ITEM = new BlockItem(ASH_HERRINGBONE_PANEL, new Item.Settings());
	public static final BlockItem GOLD_FOOD_PLATTER_ITEM = new BlockItem(GOLD_FOOD_PLATTER, new Item.Settings());
	public static final BlockItem GOLD_DRINK_TRAY_ITEM = new BlockItem(GOLD_DRINK_TRAY, new Item.Settings());
	public static final Identifier WATHE_GOLD_ORNAMENT_ID = Identifier.of("wathe", "gold_ornament");
	public static final Identifier WATHE_GOLD_LEDGE_ID = Identifier.of("wathe", "gold_ledge");
	public static final Identifier WATHE_EXTENDED_ANTHRACITE_STEEL_ORNAMENT_ID = Identifier.of("watheextended", "anthracite_steel_ornament");
	public static final Identifier WATHE_EXTENDED_KHAKI_STEEL_ORNAMENT_ID = Identifier.of("watheextended", "khaki_steel_ornament");
	public static final Identifier WATHE_EXTENDED_MAROON_STEEL_ORNAMENT_ID = Identifier.of("watheextended", "maroon_steel_ornament");
	public static final Identifier WATHE_EXTENDED_MUNTZ_STEEL_ORNAMENT_ID = Identifier.of("watheextended", "muntz_steel_ornament");
	public static final Identifier WATHE_EXTENDED_NAVY_STEEL_ORNAMENT_ID = Identifier.of("watheextended", "navy_steel_ornament");

	public static final Item GILDED_ORNAMENT_ITEM = ornamentBlockItem(WATHE_GOLD_ORNAMENT_ID);
	public static final Item GOLD_LEDGE_ITEM = new FusedLedgeItem(GOLD_LEDGE, new Item.Settings().maxCount(64), WATHE_GOLD_LEDGE_ID);
	public static final Item ANTHRACITE_LEDGE_ITEM = new FusedLedgeItem(ANTHRACITE_LEDGE, new Item.Settings().maxCount(64), Identifier.of(MapSelect.MOD_ID, "anthracite_ledge"));
	public static final Item KHAKI_LEDGE_ITEM = new FusedLedgeItem(KHAKI_LEDGE, new Item.Settings().maxCount(64), Identifier.of(MapSelect.MOD_ID, "khaki_ledge"));
	public static final Item MAROON_LEDGE_ITEM = new FusedLedgeItem(MAROON_LEDGE, new Item.Settings().maxCount(64), Identifier.of(MapSelect.MOD_ID, "maroon_ledge"));
	public static final Item MUNTZ_LEDGE_ITEM = new FusedLedgeItem(MUNTZ_LEDGE, new Item.Settings().maxCount(64), Identifier.of(MapSelect.MOD_ID, "muntz_ledge"));
	public static final Item NAVY_LEDGE_ITEM = new FusedLedgeItem(NAVY_LEDGE, new Item.Settings().maxCount(64), Identifier.of(MapSelect.MOD_ID, "navy_ledge"));
	public static final Item ANTHRACITE_STEEL_ORNAMENT_ITEM = ornamentBlockItem(WATHE_EXTENDED_ANTHRACITE_STEEL_ORNAMENT_ID);
	public static final Item KHAKI_STEEL_ORNAMENT_ITEM = ornamentBlockItem(WATHE_EXTENDED_KHAKI_STEEL_ORNAMENT_ID);
	public static final Item MAROON_STEEL_ORNAMENT_ITEM = ornamentBlockItem(WATHE_EXTENDED_MAROON_STEEL_ORNAMENT_ID);
	public static final Item MUNTZ_STEEL_ORNAMENT_ITEM = ornamentBlockItem(WATHE_EXTENDED_MUNTZ_STEEL_ORNAMENT_ID);
	public static final Item NAVY_STEEL_ORNAMENT_ITEM = ornamentBlockItem(WATHE_EXTENDED_NAVY_STEEL_ORNAMENT_ID);
	public static final BlockItem ACACIA_CABINET_ITEM = new BlockItem(ACACIA_CABINET, new Item.Settings());
	public static final BlockItem BIRCH_CABINET_ITEM = new BlockItem(BIRCH_CABINET, new Item.Settings());
	public static final BlockItem CHERRY_CABINET_ITEM = new BlockItem(CHERRY_CABINET, new Item.Settings());
	public static final BlockItem DARK_OAK_CABINET_ITEM = new BlockItem(DARK_OAK_CABINET, new Item.Settings());
	public static final BlockItem JUNGLE_CABINET_ITEM = new BlockItem(JUNGLE_CABINET, new Item.Settings());
	public static final BlockItem MANGROVE_CABINET_ITEM = new BlockItem(MANGROVE_CABINET, new Item.Settings());
	public static final BlockItem OAK_CABINET_ITEM = new BlockItem(OAK_CABINET, new Item.Settings());
	public static final BlockItem SPRUCE_CABINET_ITEM = new BlockItem(SPRUCE_CABINET, new Item.Settings());

	private static final MoquetteFamily[] MOQUETTE_FAMILIES = {
		moquetteFamily("red_moquette", Blocks.RED_WOOL),
		moquetteFamily("brown_moquette", Blocks.BROWN_WOOL),
		moquetteFamily("blue_moquette", Blocks.BLUE_WOOL),
		moquetteFamily("black_moquette", Blocks.BLACK_WOOL),
		moquetteFamily("green_moquette", Blocks.GREEN_WOOL),
		moquetteFamily("purple_moquette", Blocks.PURPLE_WOOL)
	};

	private static final Item[] ENVIRONMENTAL_BLOCK_ITEMS = {
		SAND_LAYER_ITEM,
		RED_SAND_LAYER_ITEM,
		FAKE_SUSPICIOUS_SAND_ITEM,
		FAKE_SUSPICIOUS_GRAVEL_ITEM,
		PEBBLE_BLOCK_ITEM,
		COIN_BARRIER_ITEM,
		FLOATING_TEXT_ITEM
	};

	private static final Item[] BUILDING_BLOCK_ITEMS = {
		OLIVE_PLANKS_ITEM,
		OLIVE_PLANKS_STAIRS_ITEM,
		OLIVE_PLANKS_SLAB_ITEM,
		OLIVE_PLANKS_PANEL_ITEM,
		SMOOTH_OLIVE_ITEM,
		SMOOTH_OLIVE_STAIRS_ITEM,
		SMOOTH_OLIVE_SLAB_ITEM,
		SMOOTH_OLIVE_PANEL_ITEM,
		OLIVE_HERRINGBONE_ITEM,
		OLIVE_HERRINGBONE_STAIRS_ITEM,
		OLIVE_HERRINGBONE_SLAB_ITEM,
		OLIVE_HERRINGBONE_PANEL_ITEM,
		PLUM_PLANKS_ITEM,
		PLUM_PLANKS_STAIRS_ITEM,
		PLUM_PLANKS_SLAB_ITEM,
		PLUM_PLANKS_PANEL_ITEM,
		SMOOTH_PLUM_ITEM,
		SMOOTH_PLUM_STAIRS_ITEM,
		SMOOTH_PLUM_SLAB_ITEM,
		SMOOTH_PLUM_PANEL_ITEM,
		PLUM_HERRINGBONE_ITEM,
		PLUM_HERRINGBONE_STAIRS_ITEM,
		PLUM_HERRINGBONE_SLAB_ITEM,
		PLUM_HERRINGBONE_PANEL_ITEM,
		ASH_PLANKS_ITEM,
		ASH_PLANKS_STAIRS_ITEM,
		ASH_PLANKS_SLAB_ITEM,
		ASH_PLANKS_PANEL_ITEM,
		SMOOTH_ASH_ITEM,
		SMOOTH_ASH_STAIRS_ITEM,
		SMOOTH_ASH_SLAB_ITEM,
		SMOOTH_ASH_PANEL_ITEM,
		ASH_HERRINGBONE_ITEM,
		ASH_HERRINGBONE_STAIRS_ITEM,
		ASH_HERRINGBONE_SLAB_ITEM,
		ASH_HERRINGBONE_PANEL_ITEM
	};

	private static final Item[] DECORATION_BLOCK_ITEMS = {
		GREYIFER_PLUSH_ITEM,
		IWY_PLUSH_ITEM,
		LUX_PLUSH_ITEM,
		WTFJIMJIM_PLUSH_ITEM,
		PIZZA_PLUSH_ITEM,
		JEMSEA_PLUSH_ITEM,
		PARROTMARROW_PLUSH_ITEM,
		EVIEEVEEE_PLUSH_ITEM,
		ASTRONOMIKYU_PLUSH_ITEM,
		GOLD_FOOD_PLATTER_ITEM,
		GOLD_DRINK_TRAY_ITEM,
		GILDED_ORNAMENT_ITEM,
		GOLD_LEDGE_ITEM,
		ANTHRACITE_LEDGE_ITEM,
		KHAKI_LEDGE_ITEM,
		MAROON_LEDGE_ITEM,
		MUNTZ_LEDGE_ITEM,
		NAVY_LEDGE_ITEM,
		ANTHRACITE_STEEL_ORNAMENT_ITEM,
		KHAKI_STEEL_ORNAMENT_ITEM,
		MAROON_STEEL_ORNAMENT_ITEM,
		MUNTZ_STEEL_ORNAMENT_ITEM,
		NAVY_STEEL_ORNAMENT_ITEM,
		ACACIA_CABINET_ITEM,
		BIRCH_CABINET_ITEM,
		CHERRY_CABINET_ITEM,
		DARK_OAK_CABINET_ITEM,
		JUNGLE_CABINET_ITEM,
		MANGROVE_CABINET_ITEM,
		OAK_CABINET_ITEM,
		SPRUCE_CABINET_ITEM
	};

	private static final Map<Identifier, Item> ORNAMENT_ITEMS = new HashMap<>();

	public static void register() {
		Identifier sandId = Identifier.of(MapSelect.MOD_ID, "sand_layer");
		Identifier redSandId = Identifier.of(MapSelect.MOD_ID, "red_sand_layer");
		Identifier greyiferPlushId = Identifier.of(MapSelect.MOD_ID, "greyifer_plush");
		Identifier iwyPlushId = Identifier.of(MapSelect.MOD_ID, "iwy_plush");
		Identifier luxPlushId = Identifier.of(MapSelect.MOD_ID, "lux_plush");
		Identifier wtfjimjimPlushId = Identifier.of(MapSelect.MOD_ID, "wtfjimjim_plush");
		Identifier pizzaPlushId = Identifier.of(MapSelect.MOD_ID, "pizza_plush");
		Identifier jemseaPlushId = Identifier.of(MapSelect.MOD_ID, "jemsea_plush");
		Identifier parrotmarrowPlushId = Identifier.of(MapSelect.MOD_ID, "parrotmarrow_plush");
		Identifier evieeveeePlushId = Identifier.of(MapSelect.MOD_ID, "evieeveee_plush");
		Identifier astronomikyuPlushId = Identifier.of(MapSelect.MOD_ID, "astronomikyu_plush");
		Identifier fakeSuspiciousSandId = Identifier.of(MapSelect.MOD_ID, "fake_suspicious_sand");
		Identifier fakeSuspiciousGravelId = Identifier.of(MapSelect.MOD_ID, "fake_suspicious_gravel");
		Identifier pebbleBlockId = Identifier.of(MapSelect.MOD_ID, "pebble_block");
		Identifier coinBarrierId = Identifier.of(MapSelect.MOD_ID, "coin_barrier");
		Identifier floatingTextId = Identifier.of(MapSelect.MOD_ID, "floating_text");
		Identifier olivePlanksId = Identifier.of(MapSelect.MOD_ID, "olive_planks");
		Identifier olivePlanksStairsId = Identifier.of(MapSelect.MOD_ID, "olive_planks_stairs");
		Identifier olivePlanksSlabId = Identifier.of(MapSelect.MOD_ID, "olive_planks_slab");
		Identifier olivePlanksPanelId = Identifier.of(MapSelect.MOD_ID, "olive_planks_panel");
		Identifier smoothOliveId = Identifier.of(MapSelect.MOD_ID, "smooth_olive");
		Identifier smoothOliveStairsId = Identifier.of(MapSelect.MOD_ID, "smooth_olive_stairs");
		Identifier smoothOliveSlabId = Identifier.of(MapSelect.MOD_ID, "smooth_olive_slab");
		Identifier smoothOlivePanelId = Identifier.of(MapSelect.MOD_ID, "smooth_olive_panel");
		Identifier oliveHerringboneId = Identifier.of(MapSelect.MOD_ID, "olive_herringbone");
		Identifier oliveHerringboneStairsId = Identifier.of(MapSelect.MOD_ID, "olive_herringbone_stairs");
		Identifier oliveHerringboneSlabId = Identifier.of(MapSelect.MOD_ID, "olive_herringbone_slab");
		Identifier oliveHerringbonePanelId = Identifier.of(MapSelect.MOD_ID, "olive_herringbone_panel");
		Identifier plumPlanksId = Identifier.of(MapSelect.MOD_ID, "plum_planks");
		Identifier plumPlanksStairsId = Identifier.of(MapSelect.MOD_ID, "plum_planks_stairs");
		Identifier plumPlanksSlabId = Identifier.of(MapSelect.MOD_ID, "plum_planks_slab");
		Identifier plumPlanksPanelId = Identifier.of(MapSelect.MOD_ID, "plum_planks_panel");
		Identifier smoothPlumId = Identifier.of(MapSelect.MOD_ID, "smooth_plum");
		Identifier smoothPlumStairsId = Identifier.of(MapSelect.MOD_ID, "smooth_plum_stairs");
		Identifier smoothPlumSlabId = Identifier.of(MapSelect.MOD_ID, "smooth_plum_slab");
		Identifier smoothPlumPanelId = Identifier.of(MapSelect.MOD_ID, "smooth_plum_panel");
		Identifier plumHerringboneId = Identifier.of(MapSelect.MOD_ID, "plum_herringbone");
		Identifier plumHerringboneStairsId = Identifier.of(MapSelect.MOD_ID, "plum_herringbone_stairs");
		Identifier plumHerringboneSlabId = Identifier.of(MapSelect.MOD_ID, "plum_herringbone_slab");
		Identifier plumHerringbonePanelId = Identifier.of(MapSelect.MOD_ID, "plum_herringbone_panel");
		Identifier ashPlanksId = Identifier.of(MapSelect.MOD_ID, "ash_planks");
		Identifier ashPlanksStairsId = Identifier.of(MapSelect.MOD_ID, "ash_planks_stairs");
		Identifier ashPlanksSlabId = Identifier.of(MapSelect.MOD_ID, "ash_planks_slab");
		Identifier ashPlanksPanelId = Identifier.of(MapSelect.MOD_ID, "ash_planks_panel");
		Identifier smoothAshId = Identifier.of(MapSelect.MOD_ID, "smooth_ash");
		Identifier smoothAshStairsId = Identifier.of(MapSelect.MOD_ID, "smooth_ash_stairs");
		Identifier smoothAshSlabId = Identifier.of(MapSelect.MOD_ID, "smooth_ash_slab");
		Identifier smoothAshPanelId = Identifier.of(MapSelect.MOD_ID, "smooth_ash_panel");
		Identifier ashHerringboneId = Identifier.of(MapSelect.MOD_ID, "ash_herringbone");
		Identifier ashHerringboneStairsId = Identifier.of(MapSelect.MOD_ID, "ash_herringbone_stairs");
		Identifier ashHerringboneSlabId = Identifier.of(MapSelect.MOD_ID, "ash_herringbone_slab");
		Identifier ashHerringbonePanelId = Identifier.of(MapSelect.MOD_ID, "ash_herringbone_panel");
		Identifier goldFoodPlatterId = Identifier.of(MapSelect.MOD_ID, "gold_food_platter");
		Identifier goldDrinkTrayId = Identifier.of(MapSelect.MOD_ID, "gold_drink_tray");
		Identifier fusedOrnamentedBlockId = Identifier.of(MapSelect.MOD_ID, "fused_ornamented_block");
		Identifier gildedOrnamentId = Identifier.of(MapSelect.MOD_ID, "gilded_ornament");
		Identifier goldLedgeId = Identifier.of(MapSelect.MOD_ID, "gold_ledge");
		Identifier anthraciteLedgeId = Identifier.of(MapSelect.MOD_ID, "anthracite_ledge");
		Identifier khakiLedgeId = Identifier.of(MapSelect.MOD_ID, "khaki_ledge");
		Identifier maroonLedgeId = Identifier.of(MapSelect.MOD_ID, "maroon_ledge");
		Identifier muntzLedgeId = Identifier.of(MapSelect.MOD_ID, "muntz_ledge");
		Identifier navyLedgeId = Identifier.of(MapSelect.MOD_ID, "navy_ledge");
		Identifier anthraciteSteelOrnamentId = Identifier.of(MapSelect.MOD_ID, "anthracite_steel_ornament");
		Identifier khakiSteelOrnamentId = Identifier.of(MapSelect.MOD_ID, "khaki_steel_ornament");
		Identifier maroonSteelOrnamentId = Identifier.of(MapSelect.MOD_ID, "maroon_steel_ornament");
		Identifier muntzSteelOrnamentId = Identifier.of(MapSelect.MOD_ID, "muntz_steel_ornament");
		Identifier navySteelOrnamentId = Identifier.of(MapSelect.MOD_ID, "navy_steel_ornament");
		Identifier acaciaCabinetId = Identifier.of(MapSelect.MOD_ID, "acacia_cabinet");
		Identifier birchCabinetId = Identifier.of(MapSelect.MOD_ID, "birch_cabinet");
		Identifier cherryCabinetId = Identifier.of(MapSelect.MOD_ID, "cherry_cabinet");
		Identifier darkOakCabinetId = Identifier.of(MapSelect.MOD_ID, "dark_oak_cabinet");
		Identifier jungleCabinetId = Identifier.of(MapSelect.MOD_ID, "jungle_cabinet");
		Identifier mangroveCabinetId = Identifier.of(MapSelect.MOD_ID, "mangrove_cabinet");
		Identifier oakCabinetId = Identifier.of(MapSelect.MOD_ID, "oak_cabinet");
		Identifier spruceCabinetId = Identifier.of(MapSelect.MOD_ID, "spruce_cabinet");
		Registry.register(Registries.BLOCK, sandId, SAND_LAYER);
		Registry.register(Registries.BLOCK, redSandId, RED_SAND_LAYER);
		Registry.register(Registries.BLOCK, greyiferPlushId, GREYIFER_PLUSH);
		Registry.register(Registries.BLOCK, iwyPlushId, IWY_PLUSH);
		Registry.register(Registries.BLOCK, luxPlushId, LUX_PLUSH);
		Registry.register(Registries.BLOCK, wtfjimjimPlushId, WTFJIMJIM_PLUSH);
		Registry.register(Registries.BLOCK, pizzaPlushId, PIZZA_PLUSH);
		Registry.register(Registries.BLOCK, jemseaPlushId, JEMSEA_PLUSH);
		Registry.register(Registries.BLOCK, parrotmarrowPlushId, PARROTMARROW_PLUSH);
		Registry.register(Registries.BLOCK, evieeveeePlushId, EVIEEVEEE_PLUSH);
		Registry.register(Registries.BLOCK, astronomikyuPlushId, ASTRONOMIKYU_PLUSH);
		Registry.register(Registries.BLOCK, fakeSuspiciousSandId, FAKE_SUSPICIOUS_SAND);
		Registry.register(Registries.BLOCK, fakeSuspiciousGravelId, FAKE_SUSPICIOUS_GRAVEL);
		Registry.register(Registries.BLOCK, pebbleBlockId, PEBBLE_BLOCK);
		Registry.register(Registries.BLOCK, coinBarrierId, COIN_BARRIER);
		Registry.register(Registries.BLOCK, floatingTextId, FLOATING_TEXT);
		Registry.register(Registries.BLOCK, olivePlanksId, OLIVE_PLANKS);
		Registry.register(Registries.BLOCK, olivePlanksStairsId, OLIVE_PLANKS_STAIRS);
		Registry.register(Registries.BLOCK, olivePlanksSlabId, OLIVE_PLANKS_SLAB);
		Registry.register(Registries.BLOCK, olivePlanksPanelId, OLIVE_PLANKS_PANEL);
		Registry.register(Registries.BLOCK, smoothOliveId, SMOOTH_OLIVE);
		Registry.register(Registries.BLOCK, smoothOliveStairsId, SMOOTH_OLIVE_STAIRS);
		Registry.register(Registries.BLOCK, smoothOliveSlabId, SMOOTH_OLIVE_SLAB);
		Registry.register(Registries.BLOCK, smoothOlivePanelId, SMOOTH_OLIVE_PANEL);
		Registry.register(Registries.BLOCK, oliveHerringboneId, OLIVE_HERRINGBONE);
		Registry.register(Registries.BLOCK, oliveHerringboneStairsId, OLIVE_HERRINGBONE_STAIRS);
		Registry.register(Registries.BLOCK, oliveHerringboneSlabId, OLIVE_HERRINGBONE_SLAB);
		Registry.register(Registries.BLOCK, oliveHerringbonePanelId, OLIVE_HERRINGBONE_PANEL);
		Registry.register(Registries.BLOCK, plumPlanksId, PLUM_PLANKS);
		Registry.register(Registries.BLOCK, plumPlanksStairsId, PLUM_PLANKS_STAIRS);
		Registry.register(Registries.BLOCK, plumPlanksSlabId, PLUM_PLANKS_SLAB);
		Registry.register(Registries.BLOCK, plumPlanksPanelId, PLUM_PLANKS_PANEL);
		Registry.register(Registries.BLOCK, smoothPlumId, SMOOTH_PLUM);
		Registry.register(Registries.BLOCK, smoothPlumStairsId, SMOOTH_PLUM_STAIRS);
		Registry.register(Registries.BLOCK, smoothPlumSlabId, SMOOTH_PLUM_SLAB);
		Registry.register(Registries.BLOCK, smoothPlumPanelId, SMOOTH_PLUM_PANEL);
		Registry.register(Registries.BLOCK, plumHerringboneId, PLUM_HERRINGBONE);
		Registry.register(Registries.BLOCK, plumHerringboneStairsId, PLUM_HERRINGBONE_STAIRS);
		Registry.register(Registries.BLOCK, plumHerringboneSlabId, PLUM_HERRINGBONE_SLAB);
		Registry.register(Registries.BLOCK, plumHerringbonePanelId, PLUM_HERRINGBONE_PANEL);
		Registry.register(Registries.BLOCK, ashPlanksId, ASH_PLANKS);
		Registry.register(Registries.BLOCK, ashPlanksStairsId, ASH_PLANKS_STAIRS);
		Registry.register(Registries.BLOCK, ashPlanksSlabId, ASH_PLANKS_SLAB);
		Registry.register(Registries.BLOCK, ashPlanksPanelId, ASH_PLANKS_PANEL);
		Registry.register(Registries.BLOCK, smoothAshId, SMOOTH_ASH);
		Registry.register(Registries.BLOCK, smoothAshStairsId, SMOOTH_ASH_STAIRS);
		Registry.register(Registries.BLOCK, smoothAshSlabId, SMOOTH_ASH_SLAB);
		Registry.register(Registries.BLOCK, smoothAshPanelId, SMOOTH_ASH_PANEL);
		Registry.register(Registries.BLOCK, ashHerringboneId, ASH_HERRINGBONE);
		Registry.register(Registries.BLOCK, ashHerringboneStairsId, ASH_HERRINGBONE_STAIRS);
		Registry.register(Registries.BLOCK, ashHerringboneSlabId, ASH_HERRINGBONE_SLAB);
		Registry.register(Registries.BLOCK, ashHerringbonePanelId, ASH_HERRINGBONE_PANEL);
		Registry.register(Registries.BLOCK, goldFoodPlatterId, GOLD_FOOD_PLATTER);
		Registry.register(Registries.BLOCK, goldDrinkTrayId, GOLD_DRINK_TRAY);
		Registry.register(Registries.BLOCK, goldLedgeId, GOLD_LEDGE);
		Registry.register(Registries.BLOCK, anthraciteLedgeId, ANTHRACITE_LEDGE);
		Registry.register(Registries.BLOCK, khakiLedgeId, KHAKI_LEDGE);
		Registry.register(Registries.BLOCK, maroonLedgeId, MAROON_LEDGE);
		Registry.register(Registries.BLOCK, muntzLedgeId, MUNTZ_LEDGE);
		Registry.register(Registries.BLOCK, navyLedgeId, NAVY_LEDGE);
		Registry.register(Registries.BLOCK, fusedOrnamentedBlockId, FUSED_ORNAMENTED_BLOCK);
		Registry.register(Registries.BLOCK, acaciaCabinetId, ACACIA_CABINET);
		Registry.register(Registries.BLOCK, birchCabinetId, BIRCH_CABINET);
		Registry.register(Registries.BLOCK, cherryCabinetId, CHERRY_CABINET);
		Registry.register(Registries.BLOCK, darkOakCabinetId, DARK_OAK_CABINET);
		Registry.register(Registries.BLOCK, jungleCabinetId, JUNGLE_CABINET);
		Registry.register(Registries.BLOCK, mangroveCabinetId, MANGROVE_CABINET);
		Registry.register(Registries.BLOCK, oakCabinetId, OAK_CABINET);
		Registry.register(Registries.BLOCK, spruceCabinetId, SPRUCE_CABINET);
		Registry.register(Registries.ITEM, sandId, SAND_LAYER_ITEM);
		Registry.register(Registries.ITEM, redSandId, RED_SAND_LAYER_ITEM);
		Registry.register(Registries.ITEM, greyiferPlushId, GREYIFER_PLUSH_ITEM);
		Registry.register(Registries.ITEM, iwyPlushId, IWY_PLUSH_ITEM);
		Registry.register(Registries.ITEM, luxPlushId, LUX_PLUSH_ITEM);
		Registry.register(Registries.ITEM, wtfjimjimPlushId, WTFJIMJIM_PLUSH_ITEM);
		Registry.register(Registries.ITEM, pizzaPlushId, PIZZA_PLUSH_ITEM);
		Registry.register(Registries.ITEM, jemseaPlushId, JEMSEA_PLUSH_ITEM);
		Registry.register(Registries.ITEM, parrotmarrowPlushId, PARROTMARROW_PLUSH_ITEM);
		Registry.register(Registries.ITEM, evieeveeePlushId, EVIEEVEEE_PLUSH_ITEM);
		Registry.register(Registries.ITEM, astronomikyuPlushId, ASTRONOMIKYU_PLUSH_ITEM);
		Registry.register(Registries.ITEM, fakeSuspiciousSandId, FAKE_SUSPICIOUS_SAND_ITEM);
		Registry.register(Registries.ITEM, fakeSuspiciousGravelId, FAKE_SUSPICIOUS_GRAVEL_ITEM);
		Registry.register(Registries.ITEM, pebbleBlockId, PEBBLE_BLOCK_ITEM);
		Registry.register(Registries.ITEM, coinBarrierId, COIN_BARRIER_ITEM);
		Registry.register(Registries.ITEM, floatingTextId, FLOATING_TEXT_ITEM);
		Registry.register(Registries.ITEM, olivePlanksId, OLIVE_PLANKS_ITEM);
		Registry.register(Registries.ITEM, olivePlanksStairsId, OLIVE_PLANKS_STAIRS_ITEM);
		Registry.register(Registries.ITEM, olivePlanksSlabId, OLIVE_PLANKS_SLAB_ITEM);
		Registry.register(Registries.ITEM, olivePlanksPanelId, OLIVE_PLANKS_PANEL_ITEM);
		Registry.register(Registries.ITEM, smoothOliveId, SMOOTH_OLIVE_ITEM);
		Registry.register(Registries.ITEM, smoothOliveStairsId, SMOOTH_OLIVE_STAIRS_ITEM);
		Registry.register(Registries.ITEM, smoothOliveSlabId, SMOOTH_OLIVE_SLAB_ITEM);
		Registry.register(Registries.ITEM, smoothOlivePanelId, SMOOTH_OLIVE_PANEL_ITEM);
		Registry.register(Registries.ITEM, oliveHerringboneId, OLIVE_HERRINGBONE_ITEM);
		Registry.register(Registries.ITEM, oliveHerringboneStairsId, OLIVE_HERRINGBONE_STAIRS_ITEM);
		Registry.register(Registries.ITEM, oliveHerringboneSlabId, OLIVE_HERRINGBONE_SLAB_ITEM);
		Registry.register(Registries.ITEM, oliveHerringbonePanelId, OLIVE_HERRINGBONE_PANEL_ITEM);
		Registry.register(Registries.ITEM, plumPlanksId, PLUM_PLANKS_ITEM);
		Registry.register(Registries.ITEM, plumPlanksStairsId, PLUM_PLANKS_STAIRS_ITEM);
		Registry.register(Registries.ITEM, plumPlanksSlabId, PLUM_PLANKS_SLAB_ITEM);
		Registry.register(Registries.ITEM, plumPlanksPanelId, PLUM_PLANKS_PANEL_ITEM);
		Registry.register(Registries.ITEM, smoothPlumId, SMOOTH_PLUM_ITEM);
		Registry.register(Registries.ITEM, smoothPlumStairsId, SMOOTH_PLUM_STAIRS_ITEM);
		Registry.register(Registries.ITEM, smoothPlumSlabId, SMOOTH_PLUM_SLAB_ITEM);
		Registry.register(Registries.ITEM, smoothPlumPanelId, SMOOTH_PLUM_PANEL_ITEM);
		Registry.register(Registries.ITEM, plumHerringboneId, PLUM_HERRINGBONE_ITEM);
		Registry.register(Registries.ITEM, plumHerringboneStairsId, PLUM_HERRINGBONE_STAIRS_ITEM);
		Registry.register(Registries.ITEM, plumHerringboneSlabId, PLUM_HERRINGBONE_SLAB_ITEM);
		Registry.register(Registries.ITEM, plumHerringbonePanelId, PLUM_HERRINGBONE_PANEL_ITEM);
		Registry.register(Registries.ITEM, ashPlanksId, ASH_PLANKS_ITEM);
		Registry.register(Registries.ITEM, ashPlanksStairsId, ASH_PLANKS_STAIRS_ITEM);
		Registry.register(Registries.ITEM, ashPlanksSlabId, ASH_PLANKS_SLAB_ITEM);
		Registry.register(Registries.ITEM, ashPlanksPanelId, ASH_PLANKS_PANEL_ITEM);
		Registry.register(Registries.ITEM, smoothAshId, SMOOTH_ASH_ITEM);
		Registry.register(Registries.ITEM, smoothAshStairsId, SMOOTH_ASH_STAIRS_ITEM);
		Registry.register(Registries.ITEM, smoothAshSlabId, SMOOTH_ASH_SLAB_ITEM);
		Registry.register(Registries.ITEM, smoothAshPanelId, SMOOTH_ASH_PANEL_ITEM);
		Registry.register(Registries.ITEM, ashHerringboneId, ASH_HERRINGBONE_ITEM);
		Registry.register(Registries.ITEM, ashHerringboneStairsId, ASH_HERRINGBONE_STAIRS_ITEM);
		Registry.register(Registries.ITEM, ashHerringboneSlabId, ASH_HERRINGBONE_SLAB_ITEM);
		Registry.register(Registries.ITEM, ashHerringbonePanelId, ASH_HERRINGBONE_PANEL_ITEM);
		Registry.register(Registries.ITEM, goldFoodPlatterId, GOLD_FOOD_PLATTER_ITEM);
		Registry.register(Registries.ITEM, goldDrinkTrayId, GOLD_DRINK_TRAY_ITEM);
		Registry.register(Registries.ITEM, gildedOrnamentId, GILDED_ORNAMENT_ITEM);
		Registry.register(Registries.ITEM, goldLedgeId, GOLD_LEDGE_ITEM);
		Registry.register(Registries.ITEM, anthraciteLedgeId, ANTHRACITE_LEDGE_ITEM);
		Registry.register(Registries.ITEM, khakiLedgeId, KHAKI_LEDGE_ITEM);
		Registry.register(Registries.ITEM, maroonLedgeId, MAROON_LEDGE_ITEM);
		Registry.register(Registries.ITEM, muntzLedgeId, MUNTZ_LEDGE_ITEM);
		Registry.register(Registries.ITEM, navyLedgeId, NAVY_LEDGE_ITEM);
		Registry.register(Registries.ITEM, anthraciteSteelOrnamentId, ANTHRACITE_STEEL_ORNAMENT_ITEM);
		Registry.register(Registries.ITEM, khakiSteelOrnamentId, KHAKI_STEEL_ORNAMENT_ITEM);
		Registry.register(Registries.ITEM, maroonSteelOrnamentId, MAROON_STEEL_ORNAMENT_ITEM);
		Registry.register(Registries.ITEM, muntzSteelOrnamentId, MUNTZ_STEEL_ORNAMENT_ITEM);
		Registry.register(Registries.ITEM, navySteelOrnamentId, NAVY_STEEL_ORNAMENT_ITEM);
		Registry.register(Registries.ITEM, acaciaCabinetId, ACACIA_CABINET_ITEM);
		Registry.register(Registries.ITEM, birchCabinetId, BIRCH_CABINET_ITEM);
		Registry.register(Registries.ITEM, cherryCabinetId, CHERRY_CABINET_ITEM);
		Registry.register(Registries.ITEM, darkOakCabinetId, DARK_OAK_CABINET_ITEM);
		Registry.register(Registries.ITEM, jungleCabinetId, JUNGLE_CABINET_ITEM);
		Registry.register(Registries.ITEM, mangroveCabinetId, MANGROVE_CABINET_ITEM);
		Registry.register(Registries.ITEM, oakCabinetId, OAK_CABINET_ITEM);
		Registry.register(Registries.ITEM, spruceCabinetId, SPRUCE_CABINET_ITEM);
		for (MoquetteFamily family : MOQUETTE_FAMILIES) {
			registerMoquetteFamily(family);
		}

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SEARCH).register(entries -> {
			addSpecialBlockItems(entries);
		});

		registerOrnamentItem(WATHE_GOLD_ORNAMENT_ID, GILDED_ORNAMENT_ITEM);
		registerOrnamentItem(WATHE_GOLD_LEDGE_ID, GOLD_LEDGE_ITEM);
		registerOrnamentItem(WATHE_EXTENDED_ANTHRACITE_STEEL_ORNAMENT_ID, ANTHRACITE_STEEL_ORNAMENT_ITEM);
		registerOrnamentItem(WATHE_EXTENDED_KHAKI_STEEL_ORNAMENT_ID, KHAKI_STEEL_ORNAMENT_ITEM);
		registerOrnamentItem(WATHE_EXTENDED_MAROON_STEEL_ORNAMENT_ID, MAROON_STEEL_ORNAMENT_ITEM);
		registerOrnamentItem(WATHE_EXTENDED_MUNTZ_STEEL_ORNAMENT_ID, MUNTZ_STEEL_ORNAMENT_ITEM);
		registerOrnamentItem(WATHE_EXTENDED_NAVY_STEEL_ORNAMENT_ID, NAVY_STEEL_ORNAMENT_ITEM);
	}

	public static int addSpecialBlockItems(ItemGroup.Entries entries) {
		int count = addEnvironmentalBlockItems(entries);
		count += addBuildingBlockItems(entries);
		count += addDecorationBlockItems(entries);
		return count;
	}

	public static int addEnvironmentalBlockItems(ItemGroup.Entries entries) {
		return addItems(entries, ENVIRONMENTAL_BLOCK_ITEMS);
	}

	public static int addBuildingBlockItems(ItemGroup.Entries entries) {
		int count = addItems(entries, BUILDING_BLOCK_ITEMS);
		for (MoquetteFamily family : MOQUETTE_FAMILIES) {
			entries.add(family.stairsItem());
			entries.add(family.slabItem());
			entries.add(family.panelItem());
			count += 3;
		}
		return count;
	}

	public static int addDecorationBlockItems(ItemGroup.Entries entries) {
		return addItems(entries, DECORATION_BLOCK_ITEMS);
	}

	private static int addItems(ItemGroup.Entries entries, Item[] items) {
		int count = 0;
		for (Item item : items) {
			entries.add(item);
			count++;
		}
		return count;
	}

	private static Block cabinet(MapColor mapColor) {
		return new CabinetBlock(AbstractBlock.Settings.create()
			.mapColor(mapColor)
			.strength(2.0f, 3.0f)
			.sounds(BlockSoundGroup.WOOD));
	}

	private static AbstractBlock.Settings oliveSettings() {
		return AbstractBlock.Settings.copy(Blocks.OAK_PLANKS)
			.mapColor(MapColor.DARK_GREEN)
			.strength(2.0f, 3.0f)
			.sounds(BlockSoundGroup.WOOD);
	}

	private static AbstractBlock.Settings plumSettings() {
		return AbstractBlock.Settings.copy(Blocks.OAK_PLANKS)
			.mapColor(MapColor.PURPLE)
			.strength(2.0f, 3.0f)
			.sounds(BlockSoundGroup.WOOD);
	}

	private static AbstractBlock.Settings ashSettings() {
		return AbstractBlock.Settings.copy(Blocks.OAK_PLANKS)
			.mapColor(MapColor.STONE_GRAY)
			.strength(2.0f, 3.0f)
			.sounds(BlockSoundGroup.WOOD);
	}

	private static Block stairs(Block base) {
		return new GexpressStairsBlock(base.getDefaultState(), AbstractBlock.Settings.copy(base));
	}

	private static Block slab(Block base) {
		return new SlabBlock(AbstractBlock.Settings.copy(base));
	}

	private static Block panel(Block base) {
		return new PanelBlock(AbstractBlock.Settings.copy(base));
	}

	private static Block ledge(MapColor mapColor) {
		return new GoldLedgeBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
			.mapColor(mapColor)
			.strength(0.5f)
			.sounds(BlockSoundGroup.METAL)
			.nonOpaque()
			.pistonBehavior(PistonBehavior.DESTROY));
	}

	private static MoquetteFamily moquetteFamily(String id, Block source) {
		Block stairs = stairs(source);
		Block slab = slab(source);
		Block panel = panel(source);
		return new MoquetteFamily(
			id,
			stairs,
			slab,
			panel,
			new BlockItem(stairs, new Item.Settings()),
			new BlockItem(slab, new Item.Settings()),
			new BlockItem(panel, new Item.Settings())
		);
	}

	private static void registerMoquetteFamily(MoquetteFamily family) {
		Identifier stairsId = Identifier.of(MapSelect.MOD_ID, family.id() + "_stairs");
		Identifier slabId = Identifier.of(MapSelect.MOD_ID, family.id() + "_slab");
		Identifier panelId = Identifier.of(MapSelect.MOD_ID, family.id() + "_panel");
		Registry.register(Registries.BLOCK, stairsId, family.stairs());
		Registry.register(Registries.BLOCK, slabId, family.slab());
		Registry.register(Registries.BLOCK, panelId, family.panel());
		Registry.register(Registries.ITEM, stairsId, family.stairsItem());
		Registry.register(Registries.ITEM, slabId, family.slabItem());
		Registry.register(Registries.ITEM, panelId, family.panelItem());
	}

	public static Item itemForOrnament(Identifier ornamentId) {
		return ORNAMENT_ITEMS.getOrDefault(ornamentId, GILDED_ORNAMENT_ITEM);
	}

	private static void registerOrnamentItem(Identifier ornamentId, Item item) {
		ORNAMENT_ITEMS.put(ornamentId, item);
	}

	private static Item ornamentBlockItem(Identifier ornamentId) {
		return new FusedOrnamentItem(new Item.Settings().maxCount(64), ornamentId);
	}

	private record MoquetteFamily(
		String id,
		Block stairs,
		Block slab,
		Block panel,
		BlockItem stairsItem,
		BlockItem slabItem,
		BlockItem panelItem
	) {}

	private MapSelectBlocks() {}
}
