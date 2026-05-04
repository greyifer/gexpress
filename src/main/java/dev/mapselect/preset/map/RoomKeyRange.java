package dev.mapselect.preset.map;

import dev.mapselect.MapSelect;
import dev.mapselect.weather.MapWeatherComponent;
import net.minecraft.server.world.ServerWorld;

import java.io.IOException;

public final class RoomKeyRange {
	private RoomKeyRange() {}

	public static int forWorld(ServerWorld world) {
		if (world == null || world.getServer() == null) return MapPreset.DEFAULT_ROOM_COUNT;

		String mapName = MapWeatherComponent.KEY.get(world).getCurrentMapName();
		if (mapName == null || mapName.isBlank() || !PresetStorage.isValidName(mapName)) {
			return MapPreset.DEFAULT_ROOM_COUNT;
		}

		try {
			MapPreset preset = PresetStorage.load(world.getServer(), mapName);
			return preset == null ? MapPreset.DEFAULT_ROOM_COUNT : preset.normalizedRoomCount();
		} catch (IOException e) {
			MapSelect.LOGGER.warn("Failed to load room key count for active map '{}'", mapName, e);
			return MapPreset.DEFAULT_ROOM_COUNT;
		}
	}
}
