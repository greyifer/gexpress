package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

final class RegistryHelper {
	private RegistryHelper() {}

	static Identifier id(String path) {
		return Identifier.of(MapSelect.MOD_ID, path);
	}

	static <V, T extends V> T register(Registry<V> registry, String path, T entry) {
		return register(registry, id(path), entry);
	}

	static <V, T extends V> T register(Registry<V> registry, Identifier id, T entry) {
		return Registry.register(registry, id, entry);
	}
}
