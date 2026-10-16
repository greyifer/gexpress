package dev.mapselect.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;

public final class MapSelectParticles {
	public static final SimpleParticleType SAND_DRIFT = FabricParticleTypes.simple();

	public static void register() {
		RegistryHelper.register(Registries.PARTICLE_TYPE, "sand_drift", SAND_DRIFT);
	}

	private MapSelectParticles() {}
}
