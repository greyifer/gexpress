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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FreshAirAreaManager {
	private FreshAirAreaManager() {}

	private static final Map<RegistryKey<World>, CachedArea> CACHE = new ConcurrentHashMap<>();

	public static boolean countsAsFreshAir(Entity entity) {
		if (entity == null || !(entity.getWorld() instanceof ServerWorld world)) return false;
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(world);
		String currentMap = weather == null ? null : weather.getCurrentMapName();
		if (currentMap == null || currentMap.isBlank()) currentMap = "*";

		CachedArea cached = CACHE.get(world.getRegistryKey());
		if (cached == null || !currentMap.equals(cached.mapName())) {
			cached = "*".equals(currentMap) ? loadAllAreas(world) : loadArea(world, currentMap);
			CACHE.put(world.getRegistryKey(), cached);
		}

		if (cached.areas().isEmpty()) return false;
		Box entityBox = entity.getBoundingBox().expand(0.05D, 0.1D, 0.05D);
		for (FreshAirArea area : cached.areas()) {
			if (area.box().intersects(entityBox)
					|| area.box().contains(entity.getPos())
					|| area.box().contains(entity.getEyePos())) {
				return true;
			}
		}
		return false;
	}

	public static int sanityPercent(Entity entity) {
		if (entity == null || !(entity.getWorld() instanceof ServerWorld world)) return 100;
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(world);
		String currentMap = weather == null ? null : weather.getCurrentMapName();
		if (currentMap == null || currentMap.isBlank()) currentMap = "*";
		CachedArea cached = CACHE.get(world.getRegistryKey());
		if (cached == null || !currentMap.equals(cached.mapName())) {
			cached = "*".equals(currentMap) ? loadAllAreas(world) : loadArea(world, currentMap);
			CACHE.put(world.getRegistryKey(), cached);
		}
		Box entityBox = entity.getBoundingBox().expand(0.05D, 0.1D, 0.05D);
		for (FreshAirArea area : cached.areas()) {
			if (area.box().intersects(entityBox)
					|| area.box().contains(entity.getPos())
					|| area.box().contains(entity.getEyePos())) {
				return area.sanityPercent();
			}
		}
		return 100;
	}

	public static void clearCache(ServerWorld world) {
		if (world != null) CACHE.remove(world.getRegistryKey());
	}

	private static CachedArea loadArea(ServerWorld world, String mapName) {
		try {
			MapPreset preset = PresetStorage.load(world.getServer(), mapName);
			return new CachedArea(mapName, areasFrom(preset));
		} catch (IOException e) {
			return new CachedArea(mapName, List.of());
		}
	}

	private static CachedArea loadAllAreas(ServerWorld world) {
		List<FreshAirArea> areas = new ArrayList<>();
		try {
			for (String name : PresetStorage.list(world.getServer())) {
				MapPreset preset = PresetStorage.load(world.getServer(), name);
				areas.addAll(areasFrom(preset));
			}
		} catch (IOException ignored) {
		}
		return new CachedArea("*", List.copyOf(areas));
	}

	private static List<FreshAirArea> areasFrom(MapPreset preset) {
		if (preset == null) return List.of();
		preset.normalize();
		List<FreshAirArea> out = new ArrayList<>();
		if (preset.freshAirAreas != null) {
			for (MapPreset.FreshAirAreaData entry : preset.freshAirAreas) {
				if (entry == null || entry.area == null) continue;
				out.add(new FreshAirArea(entry.area.toBox(), Math.max(0, Math.min(100, entry.sanityPercent))));
			}
		}
		return List.copyOf(out);
	}

	private record CachedArea(String mapName, List<FreshAirArea> areas) {}
	private record FreshAirArea(Box box, int sanityPercent) {}
}
