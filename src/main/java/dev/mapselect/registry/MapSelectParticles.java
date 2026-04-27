package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class MapSelectParticles {
	public static final SimpleParticleType SAND_DRIFT = FabricParticleTypes.simple();

	public static void register() {
		Registry.register(Registries.PARTICLE_TYPE, Identifier.of(MapSelect.MOD_ID, "sand_drift"), SAND_DRIFT);
	}

	private MapSelectParticles() {}
}
