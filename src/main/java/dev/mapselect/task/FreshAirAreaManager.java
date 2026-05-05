package dev.mapselect.task;

import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.weather.MapWeatherComponent;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FreshAirAreaManager {
	private FreshAirAreaManager() {}

	private static final Map<RegistryKey<World>, CachedArea> CACHE = new ConcurrentHashMap<>();

	public static boolean countsAsFreshAir(Entity entity) {
		if (entity == null || !(entity.getWorld() instanceof ServerWorld world)) return false;
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(world);
		String currentMap = weather == null ? null : weather.getCurrentMapName();
		if (currentMap == null || currentMap.isBlank()) return false;

		CachedArea cached = CACHE.get(world.getRegistryKey());
		if (cached == null || !currentMap.equals(cached.mapName())) {
			cached = loadArea(world, currentMap);
			CACHE.put(world.getRegistryKey(), cached);
		}

		Box area = cached.area();
		return area != null && (area.contains(entity.getPos()) || area.contains(entity.getEyePos()));
	}

	public static void clearCache(ServerWorld world) {
		if (world != null) CACHE.remove(world.getRegistryKey());
	}

	private static CachedArea loadArea(ServerWorld world, String mapName) {
		try {
			MapPreset preset = PresetStorage.load(world.getServer(), mapName);
			if (preset == null || preset.freshAirArea == null) return new CachedArea(mapName, null);
			return new CachedArea(mapName, preset.freshAirArea.toBox());
		} catch (IOException e) {
			return new CachedArea(mapName, null);
		}
	}

	private record CachedArea(String mapName, Box area) {}
}
