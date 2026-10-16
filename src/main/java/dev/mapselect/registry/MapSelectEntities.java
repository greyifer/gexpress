package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import dev.mapselect.entity.TwinBodyEntity;
import dev.mapselect.entity.RatTrapEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class MapSelectEntities {
	public static EntityType<TwinBodyEntity> TWIN_BODY;
	public static EntityType<RatTrapEntity> RAT_TRAP;

	private MapSelectEntities() {}

	public static void register() {
		TWIN_BODY = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "twin_body"),
			FabricEntityTypeBuilder.create(SpawnGroup.MISC, TwinBodyEntity::new)
				.dimensions(EntityDimensions.fixed(0.6F, 1.8F))
				.trackRangeBlocks(256)
				.trackedUpdateRate(2)
				.forceTrackedVelocityUpdates(true)
				.build());
		FabricDefaultAttributeRegistry.register(TWIN_BODY, MobEntity.createMobAttributes());
		RAT_TRAP = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(MapSelect.MOD_ID, "rat_trap"),
			FabricEntityTypeBuilder.<RatTrapEntity>create(SpawnGroup.MISC, RatTrapEntity::new)
				.dimensions(EntityDimensions.fixed(0.42F, 0.10F))
				.trackRangeBlocks(64)
				.trackedUpdateRate(10)
				.build());
	}
}
