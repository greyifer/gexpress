package dev.mapselect.preset.map;

import dev.mapselect.preset.train.TrainPreset;
import dev.mapselect.preset.train.TrainPresetStorage;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MapPresetValidator {
	private MapPresetValidator() {}

	public static List<Issue> validate(MinecraftServer server, String name, MapPreset preset) {
		List<Issue> issues = new ArrayList<>();
		if (preset == null) {
			issues.add(Issue.error("Preset is missing or could not be loaded."));
			return List.copyOf(issues);
		}
		preset.normalize();
		requireBox(issues, "whole map area", preset.wholeMapArea);
		requireBox(issues, "play area", preset.playArea);
		requireBox(issues, "reset template area", preset.resetTemplateArea);
		requirePos(issues, "spectator spawn", preset.spectatorSpawnPos);
		requirePos(issues, "ready-area spawn", preset.readyAreaSpawnPos);
		if (preset.playAreaMode == MapPreset.PlayAreaMode.TRAIN) {
			validateTrainMode(server, preset, issues);
		} else {
			if (preset.wholeMapArea != null && preset.playArea != null && !sameBox(preset.wholeMapArea, preset.playArea)) {
				issues.add(Issue.warning("Full-map mode play area differs from whole map area; recalculate before saving."));
			}
		}
		if (preset.terrainMode == MapPreset.TerrainMode.STATIC && preset.wholeMapArea == null) {
			issues.add(Issue.warning("Static maps should define a whole map area for reset tracking."));
		}
		if (preset.playArea != null && preset.resetTemplateArea != null && preset.playAreaOffset == null) {
			issues.add(Issue.warning("Play area offset is missing; derived map movement may be wrong."));
		}
		if (preset.roomCount != MapPreset.normalizeRoomCount(preset.roomCount)) {
			issues.add(Issue.warning("Room key count will be normalized to " + MapPreset.normalizeRoomCount(preset.roomCount) + "."));
		}
		if (preset.randomSpawnPositions == null || preset.randomSpawnPositions.isEmpty()) {
			issues.add(Issue.warning("No random spawn positions are saved."));
		} else if (preset.playArea != null) {
			long outside = preset.randomSpawnPositions.stream().filter(pos -> !contains(preset.playArea, pos)).count();
			if (outside > 0) issues.add(Issue.warning(outside + " random spawn(s) are outside the play area."));
		}
		if (preset.freshAirAreas == null || preset.freshAirAreas.isEmpty()) {
			issues.add(Issue.warning("No fresh-air areas are configured."));
		}
		return List.copyOf(issues);
	}

	private static void validateTrainMode(MinecraftServer server, MapPreset preset, List<Issue> issues) {
		if (preset.defaultTrainPreset == null || preset.defaultTrainPreset.isBlank()) {
			issues.add(Issue.error("Train play-area mode requires a default train preset."));
			return;
		}
		if (server == null) return;
		try {
			if (!TrainPresetStorage.exists(server, preset.defaultTrainPreset)) {
				issues.add(Issue.error("Default train preset '" + preset.defaultTrainPreset + "' does not exist."));
				return;
			}
			TrainPreset train = TrainPresetStorage.load(server, preset.defaultTrainPreset);
			if (train == null) {
				issues.add(Issue.error("Default train preset '" + preset.defaultTrainPreset + "' could not be loaded."));
				return;
			}
			if (train.resetTemplateArea == null) issues.add(Issue.warning("Default train has no reset template area."));
			if (train.slotCount() == 0) issues.add(Issue.warning("Default train has no teleportation slots."));
			if (train.cartCount() == 0) issues.add(Issue.warning("Default train has no cart play-area boxes."));
		} catch (IOException error) {
			issues.add(Issue.error("Could not validate default train preset: " + error.getMessage()));
		}
	}

	private static void requireBox(List<Issue> issues, String label, MapPreset.BoxData box) {
		if (box == null) issues.add(Issue.error("Missing " + label + "."));
	}

	private static void requirePos(List<Issue> issues, String label, MapPreset.PosData pos) {
		if (pos == null) issues.add(Issue.warning("Missing " + label + "."));
	}

	private static boolean contains(MapPreset.BoxData box, MapPreset.PosData pos) {
		return pos != null && pos.x >= box.minX && pos.x <= box.maxX
			&& pos.y >= box.minY && pos.y <= box.maxY
			&& pos.z >= box.minZ && pos.z <= box.maxZ;
	}

	private static boolean sameBox(MapPreset.BoxData a, MapPreset.BoxData b) {
		return close(a.minX, b.minX) && close(a.minY, b.minY) && close(a.minZ, b.minZ)
			&& close(a.maxX, b.maxX) && close(a.maxY, b.maxY) && close(a.maxZ, b.maxZ);
	}

	private static boolean close(double a, double b) {
		return Math.abs(a - b) < 1.0E-6D;
	}

	public record Issue(Severity severity, String message) {
		public static Issue error(String message) {
			return new Issue(Severity.ERROR, message);
		}

		public static Issue warning(String message) {
			return new Issue(Severity.WARNING, message);
		}
	}

	public enum Severity {
		ERROR,
		WARNING
	}
}
