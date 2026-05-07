package dev.mapselect.task;

import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.network.FreshAirAmbienceStatePayload;
import dev.mapselect.weather.MapWeatherComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
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

	public static final String TAG = "gexpress_fresh_air_area";
	public static final String AMBIENCE_TAG = "gexpress_fresh_air_ambience";
	private static final Map<RegistryKey<World>, CachedArea> CACHE = new ConcurrentHashMap<>();
	private static volatile boolean clientLocalFreshAirAmbience;
	private static int tickDelay;

	public static void register() {
		PayloadTypeRegistry.playS2C().register(FreshAirAmbienceStatePayload.ID, FreshAirAmbienceStatePayload.CODEC);
		ServerTickEvents.END_WORLD_TICK.register(FreshAirAreaManager::tick);
	}

	public static boolean countsAsFreshAir(Entity entity) {
		if (entity == null || !(entity.getWorld() instanceof ServerWorld world)) return false;
		if (hasFreshAirTag(entity)) return true;
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(world);
		String currentMap = weather == null ? null : weather.getCurrentMapName();
		if (currentMap == null || currentMap.isBlank()) currentMap = "*";

		CachedArea cached = CACHE.get(world.getRegistryKey());
		if (cached == null || !currentMap.equals(cached.mapName())) {
			cached = "*".equals(currentMap) ? loadAllAreas(world) : loadArea(world, currentMap);
			CACHE.put(world.getRegistryKey(), cached);
		}

		if (cached.areas().isEmpty() && !"*".equals(currentMap)) {
			cached = loadAllAreas(world);
			CACHE.put(world.getRegistryKey(), new CachedArea(currentMap, cached.areas()));
		}
		if (cached.areas().isEmpty()) return false;
		for (FreshAirArea area : cached.areas()) {
			if (matches(area, entity)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasFreshAirTag(Entity entity) {
		return entity != null && entity.getCommandTags().contains(TAG);
	}

	public static boolean hasFreshAirAmbienceTag(Entity entity) {
		return entity != null && entity.getCommandTags().contains(AMBIENCE_TAG);
	}

	public static void setClientLocalFreshAirAmbience(boolean playOutsideAmbience) {
		clientLocalFreshAirAmbience = playOutsideAmbience;
	}

	public static boolean shouldPlayClientFreshAirAmbience(Entity entity) {
		return entity != null && entity.getWorld().isClient && clientLocalFreshAirAmbience;
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
		for (FreshAirArea area : cached.areas()) {
			if (matches(area, entity)) {
				return area.sanityPercent();
			}
		}
		return 100;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (tickDelay++ % 5 != 0) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			FreshAirArea area = matchingArea(player);
			if (area != null) {
				player.addCommandTag(TAG);
				if (area.playOutsideAmbience()) {
					player.addCommandTag(AMBIENCE_TAG);
				} else if (hasFreshAirAmbienceTag(player)) {
					player.removeCommandTag(AMBIENCE_TAG);
				}
				syncAmbience(player, area.playOutsideAmbience());
			} else if (hasFreshAirTag(player)) {
				player.removeCommandTag(TAG);
				player.removeCommandTag(AMBIENCE_TAG);
				syncAmbience(player, false);
			} else if (hasFreshAirAmbienceTag(player)) {
				player.removeCommandTag(AMBIENCE_TAG);
				syncAmbience(player, false);
			} else {
				syncAmbience(player, false);
			}
		}
	}

	private static void syncAmbience(ServerPlayerEntity player, boolean playOutsideAmbience) {
		if (player == null || !ServerPlayNetworking.canSend(player, FreshAirAmbienceStatePayload.ID)) return;
		ServerPlayNetworking.send(player, new FreshAirAmbienceStatePayload(playOutsideAmbience));
	}

	private static FreshAirArea matchingArea(Entity entity) {
		if (entity == null || !(entity.getWorld() instanceof ServerWorld world)) return null;
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(world);
		String currentMap = weather == null ? null : weather.getCurrentMapName();
		if (currentMap == null || currentMap.isBlank()) currentMap = "*";
		CachedArea cached = "*".equals(currentMap) ? loadAllAreas(world) : loadArea(world, currentMap);
		if (cached.areas().isEmpty() && !"*".equals(currentMap)) {
			cached = loadAllAreas(world);
		}
		if (cached.areas().isEmpty()) return null;
		for (FreshAirArea area : cached.areas()) {
			if (matches(area, entity)) {
				return area;
			}
		}
		return null;
	}

	private static boolean matches(FreshAirArea area, Entity entity) {
		Box box = usableBox(area.box());
		Box entityBox = entity.getBoundingBox().expand(0.1D, 0.2D, 0.1D);
		return box.intersects(entityBox)
			|| box.contains(entity.getPos())
			|| box.contains(entity.getEyePos());
	}

	private static Box usableBox(Box box) {
		double minX = Math.min(box.minX, box.maxX);
		double minY = Math.min(box.minY, box.maxY);
		double minZ = Math.min(box.minZ, box.maxZ);
		double maxX = Math.max(box.minX, box.maxX);
		double maxY = Math.max(box.minY, box.maxY);
		double maxZ = Math.max(box.minZ, box.maxZ);
		if (maxX - minX < 1.0D) maxX = minX + 1.0D;
		else maxX += 1.0D;
		if (maxY - minY < 1.0D) maxY = minY + 1.0D;
		else maxY += 1.0D;
		if (maxZ - minZ < 1.0D) maxZ = minZ + 1.0D;
		else maxZ += 1.0D;
		return new Box(minX, minY, minZ, maxX, maxY, maxZ);
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
				out.add(new FreshAirArea(entry.area.toBox(), Math.max(0, Math.min(100, entry.sanityPercent)),
					entry.playOutsideAmbience));
			}
		}
		return List.copyOf(out);
	}

	private record CachedArea(String mapName, List<FreshAirArea> areas) {}
	private record FreshAirArea(Box box, int sanityPercent, boolean playOutsideAmbience) {}
}
