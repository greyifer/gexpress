package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import dev.mapselect.block.CoinBarrierBlockEntity;
import dev.mapselect.block.FusedOrnamentBlockEntity;
import dev.mapselect.block.FloatingTextBlockEntity;
import dev.mapselect.block.GoldBeveragePlateBlockEntity;
import dev.mapselect.block.GreyiferPlushBlockEntity;
import dev.mapselect.block.KeycardReaderBlockEntity;
import dev.mapselect.block.LargeWoodDoorBlockEntity;
import dev.mapselect.block.LeaderboardBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class MapSelectBlockEntities {
	private MapSelectBlockEntities() {}

	public static BlockEntityType<GreyiferPlushBlockEntity> GREYIFER_PLUSH;
	public static BlockEntityType<GoldBeveragePlateBlockEntity> GOLD_BEVERAGE_PLATE;
	public static BlockEntityType<FusedOrnamentBlockEntity> FUSED_ORNAMENT;
	public static BlockEntityType<CoinBarrierBlockEntity> COIN_BARRIER;
	public static BlockEntityType<FloatingTextBlockEntity> FLOATING_TEXT;
	public static BlockEntityType<KeycardReaderBlockEntity> KEYCARD_READER;
	public static BlockEntityType<LargeWoodDoorBlockEntity> LARGE_WOOD_DOOR;
	public static BlockEntityType<LeaderboardBlockEntity> LEADERBOARD;
	public static BlockEntityType<dev.mapselect.block.SettingsPanelBlockEntity> SETTINGS_PANEL;

	public static void register() {
		GREYIFER_PLUSH = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "greyifer_plush"),
			BlockEntityType.Builder.create(GreyiferPlushBlockEntity::new,
				MapSelectBlocks.GREYIFER_PLUSH,
				MapSelectBlocks.IWY_PLUSH,
				MapSelectBlocks.LUX_PLUSH,
				MapSelectBlocks.WTFJIMJIM_PLUSH,
				MapSelectBlocks.PIZZA_PLUSH,
				MapSelectBlocks.JEMSEA_PLUSH,
				MapSelectBlocks.PARROTMARROW_PLUSH,
				MapSelectBlocks.EVIEEVEEE_PLUSH,
				MapSelectBlocks.ETH4N1_PLUSH,
				MapSelectBlocks.DOMEKIQN_PLUSH,
				MapSelectBlocks.SEAFOX_PLUSH,
				MapSelectBlocks.REDDISHACORN_PLUSH,
				MapSelectBlocks.ASTRONOMIKYU_PLUSH,
				MapSelectBlocks.LIV_54_PLUSH).build(null)
		);
		GOLD_BEVERAGE_PLATE = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "gold_beverage_plate"),
			BlockEntityType.Builder.create(GoldBeveragePlateBlockEntity::new,
				MapSelectBlocks.GOLD_FOOD_PLATTER,
				MapSelectBlocks.GOLD_DRINK_TRAY).build(null)
		);
		FUSED_ORNAMENT = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "fused_ornament"),
			BlockEntityType.Builder.create(FusedOrnamentBlockEntity::new,
				MapSelectBlocks.FUSED_ORNAMENTED_BLOCK).build(null)
		);
		COIN_BARRIER = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "coin_barrier"),
			BlockEntityType.Builder.create(CoinBarrierBlockEntity::new,
				MapSelectBlocks.COIN_BARRIER).build(null)
		);
		FLOATING_TEXT = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "floating_text"),
			BlockEntityType.Builder.create(FloatingTextBlockEntity::new,
				MapSelectBlocks.FLOATING_TEXT).build(null)
		);
		KEYCARD_READER = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "keycard_reader"),
			BlockEntityType.Builder.create(KeycardReaderBlockEntity::new,
				MapSelectBlocks.KEYCARD_READER).build(null)
		);
		LARGE_WOOD_DOOR = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "large_wood_door"),
			BlockEntityType.Builder.create(LargeWoodDoorBlockEntity::new,
				MapSelectBlocks.LARGE_WOOD_DOOR).build(null)
		);
		LEADERBOARD = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "leaderboard"),
			BlockEntityType.Builder.create(LeaderboardBlockEntity::new,
				MapSelectBlocks.LEADERBOARD).build(null)
		);
		SETTINGS_PANEL = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MapSelect.MOD_ID, "settings_panel"),
			BlockEntityType.Builder.create(dev.mapselect.block.SettingsPanelBlockEntity::new, MapSelectBlocks.SETTINGS_PANEL).build(null));
	}
}
