package dev.mapselect.preset.map;

import dev.mapselect.preset.train.TrainPreset;
import dev.mapselect.preset.train.TrainPresetStorage;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
		} else {
			if (preset.randomSpawnPositions.size() == 1) {
				issues.add(Issue.warning("Only one random spawn position is saved; rounds may stack players."));
			}
			int duplicates = duplicateSpawnBlocks(preset.randomSpawnPositions);
			if (duplicates > 0) {
				issues.add(Issue.warning(duplicates + " random spawn(s) share a block with another spawn."));
			}
			if (preset.wholeMapArea != null) {
				long outsideWholeMap = preset.randomSpawnPositions.stream().filter(pos -> !contains(preset.wholeMapArea, pos)).count();
				if (outsideWholeMap > 0) issues.add(Issue.warning(outsideWholeMap + " random spawn(s) are outside the whole map area."));
			}
			if (preset.playArea != null) {
				long outside = preset.randomSpawnPositions.stream().filter(pos -> !contains(preset.playArea, pos)).count();
				if (outside > 0) issues.add(Issue.warning(outside + " random spawn(s) are outside the play area."));
				long nearBoundary = preset.randomSpawnPositions.stream()
					.filter(pos -> contains(preset.playArea, pos) && nearBoundary(preset.playArea, pos, 1.0D))
					.count();
				if (nearBoundary > 0) {
					issues.add(Issue.warning(nearBoundary + " random spawn(s) are within 1 block of the play-area boundary."));
				}
			}
		}
		if (preset.freshAirAreas == null || preset.freshAirAreas.isEmpty()) {
			issues.add(Issue.warning("No fresh-air areas are configured."));
		}
		validateOverrideIds("role chance override", preset.roleChanceOverrides.keySet(), issues);
		validateOverrideIds("role max override", preset.roleMaxOverrides.keySet(), issues);
		validateOverrideIds("modifier chance override", preset.modifierChanceOverrides.keySet(), issues);
		validateOverrideIds("modifier max override", preset.modifierMaxOverrides.keySet(), issues);
		validateGameSettingsPreset(server, preset, issues);
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

	private static boolean nearBoundary(MapPreset.BoxData box, MapPreset.PosData pos, double margin) {
		return pos.x - box.minX <= margin || box.maxX - pos.x <= margin
			|| pos.y - box.minY <= margin || box.maxY - pos.y <= margin
			|| pos.z - box.minZ <= margin || box.maxZ - pos.z <= margin;
	}

	private static int duplicateSpawnBlocks(List<MapPreset.PosData> positions) {
		Set<String> seen = new HashSet<>();
		int duplicates = 0;
		for (MapPreset.PosData pos : positions) {
			if (pos == null) continue;
			String key = (int) Math.floor(pos.x) + "," + (int) Math.floor(pos.y) + "," + (int) Math.floor(pos.z);
			if (!seen.add(key)) duplicates++;
		}
		return duplicates;
	}

	private static boolean sameBox(MapPreset.BoxData a, MapPreset.BoxData b) {
		return close(a.minX, b.minX) && close(a.minY, b.minY) && close(a.minZ, b.minZ)
			&& close(a.maxX, b.maxX) && close(a.maxY, b.maxY) && close(a.maxZ, b.maxZ);
	}

	private static boolean close(double a, double b) {
		return Math.abs(a - b) < 1.0E-6D;
	}

	private static void validateOverrideIds(String label, Set<String> ids, List<Issue> issues) {
		if (ids == null || ids.isEmpty()) return;
		for (String id : ids) {
			if (Identifier.tryParse(id) == null) {
				issues.add(Issue.warning("Invalid " + label + " id '" + id + "'."));
			}
		}
	}

	private static void validateGameSettingsPreset(MinecraftServer server, MapPreset preset, List<Issue> issues) {
		if (preset.gameSettingsPreset == null || preset.gameSettingsPreset.isBlank()) return;
		if (!GexpressConfig.isValidExportName(preset.gameSettingsPreset)) {
			issues.add(Issue.error("Game settings preset name '" + preset.gameSettingsPreset + "' is invalid."));
			return;
		}
		try {
			if (!GexpressConfig.listConfigExports().contains(preset.gameSettingsPreset)) {
				issues.add(Issue.warning("Game settings preset '" + preset.gameSettingsPreset + "' does not exist."));
			}
		} catch (IOException error) {
			issues.add(Issue.warning("Could not validate game settings preset: " + error.getMessage()));
		}
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
