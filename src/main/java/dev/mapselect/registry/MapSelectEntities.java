package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import dev.mapselect.entity.TutorialPassengerEntity;
import dev.mapselect.entity.TwinBodyEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class MapSelectEntities {
	public static EntityType<TutorialPassengerEntity> TUTORIAL_PASSENGER;
	public static EntityType<TwinBodyEntity> TWIN_BODY;

	private MapSelectEntities() {}

	public static void register() {
		TUTORIAL_PASSENGER = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "tutorial_passenger"),
			FabricEntityTypeBuilder.create(SpawnGroup.MISC, TutorialPassengerEntity::new)
				.dimensions(EntityDimensions.fixed(0.6F, 1.8F))
				.trackRangeBlocks(64)
				.build());
		FabricDefaultAttributeRegistry.register(TUTORIAL_PASSENGER, ZombieEntity.createZombieAttributes());

		TWIN_BODY = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "twin_body"),
			FabricEntityTypeBuilder.create(SpawnGroup.MISC, TwinBodyEntity::new)
				.dimensions(EntityDimensions.fixed(0.6F, 1.8F))
				.trackRangeBlocks(256)
				.trackedUpdateRate(10)
				.build());
		FabricDefaultAttributeRegistry.register(TWIN_BODY, ZombieEntity.createZombieAttributes());
	}
}
