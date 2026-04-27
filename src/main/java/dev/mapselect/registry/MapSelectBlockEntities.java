package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import dev.mapselect.block.GreyiferPlushBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class MapSelectBlockEntities {
	private MapSelectBlockEntities() {}

	public static BlockEntityType<GreyiferPlushBlockEntity> GREYIFER_PLUSH;

	public static void register() {
		GREYIFER_PLUSH = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "greyifer_plush"),
			BlockEntityType.Builder.create(GreyiferPlushBlockEntity::new, MapSelectBlocks.GREYIFER_PLUSH).build(null)
		);
	}
}
