package dev.mapselect.preset.map;

import dev.mapselect.MapSelect;
import dev.mapselect.weather.WeatherType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.ArrayList;

public final class DefaultMapPresets {
	public static final String STARTING_MAP = "snow";

	private DefaultMapPresets() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(DefaultMapPresets::ensureBundledDefaults);
	}

	public static void ensureBundledDefaults(MinecraftServer server) {
		try {
			if (!PresetStorage.exists(server, STARTING_MAP)) {
				PresetStorage.save(server, STARTING_MAP, startingMap());
				MapSelect.LOGGER.info("Seeded bundled G'Express map preset '{}'.", STARTING_MAP);
			}
		} catch (IOException e) {
			MapSelect.LOGGER.warn("Failed to seed bundled G'Express map preset '{}': {}", STARTING_MAP, e.toString());
		}
	}

	private static MapPreset startingMap() {
		MapPreset preset = new MapPreset();
		preset.wholeMapArea = box(-200.0D, 0.0D, -896.0D, 303.0D, 0.0D, -177.0D);
		preset.lobbyArea = box(-1424.0D, -50.0D, -520.5D, -763.0D, 50.0D, -512.0D);
		preset.readyArea = box(-1016.5D, -1.0D, -363.75D, -715.5D, 6.0D, -357.25D);
		preset.playArea = box(-138.0D, 118.0D, -546.0D, 326.0D, 200.0D, -528.0D);
		preset.resetTemplateArea = box(-57.0D, 64.0D, -541.0D, 273.0D, 74.0D, -531.0D);
		preset.readyAreaSpawnPos = pos(-909.5D, 1.0D, -360.5D, -90.0F, 0.0F);
		preset.spectatorSpawnPos = pos(-67.5D, 133.0D, 194.5D, -90.0F, 15.0F);
		preset.playAreaOffset = offset(903, 121, -175);
		preset.lobbyTrainCorner = offset(-1020, -2, -368);
		preset.weather = WeatherType.SNOW;
		preset.fogColor = null;
		preset.defaultTrainPreset = "snow";
		preset.roomCount = 7;
		preset.freshAirAreas = new ArrayList<>();
		preset.freshAirArea = null;
		preset.normalize();
		return preset;
	}

	private static MapPreset.BoxData box(double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ) {
		MapPreset.BoxData box = new MapPreset.BoxData();
		box.minX = minX;
		box.minY = minY;
		box.minZ = minZ;
		box.maxX = maxX;
		box.maxY = maxY;
		box.maxZ = maxZ;
		return box;
	}

	private static MapPreset.PosData pos(double x, double y, double z, float yaw, float pitch) {
		MapPreset.PosData pos = new MapPreset.PosData();
		pos.x = x;
		pos.y = y;
		pos.z = z;
		pos.yaw = yaw;
		pos.pitch = pitch;
		return pos;
	}

	private static MapPreset.OffsetData offset(int x, int y, int z) {
		MapPreset.OffsetData offset = new MapPreset.OffsetData();
		offset.x = x;
		offset.y = y;
		offset.z = z;
		return offset;
	}
}
