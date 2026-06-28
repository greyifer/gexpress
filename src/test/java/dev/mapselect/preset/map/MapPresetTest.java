package dev.mapselect.preset.map;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPresetTest {
	@Test
	void normalizeReordersBoxesAndClampsVisuals() {
		MapPreset preset = new MapPreset();
		preset.playArea = new MapPreset.BoxData();
		preset.playArea.minX = 10;
		preset.playArea.minY = 70;
		preset.playArea.minZ = 5;
		preset.playArea.maxX = -2;
		preset.playArea.maxY = 64;
		preset.playArea.maxZ = -8;
		preset.fogColor = 0xFFABCDEF;
		preset.defaultTrainPreset = "  train_a  ";

		preset.normalize();

		assertEquals(-2, preset.playArea.minX);
		assertEquals(64, preset.playArea.minY);
		assertEquals(-8, preset.playArea.minZ);
		assertEquals(10, preset.playArea.maxX);
		assertEquals(70, preset.playArea.maxY);
		assertEquals(5, preset.playArea.maxZ);
		assertEquals(0xABCDEF, preset.fogColor);
		assertEquals("train_a", preset.defaultTrainPreset);
	}

	@Test
	void normalizeDropsInvalidPositions() {
		MapPreset preset = new MapPreset();
		preset.randomSpawnPositions = new ArrayList<>();
		MapPreset.PosData good = new MapPreset.PosData();
		good.x = 1;
		good.y = 2;
		good.z = 3;
		good.yaw = 400;
		good.pitch = 100;
		MapPreset.PosData bad = new MapPreset.PosData();
		bad.x = Double.NaN;
		bad.y = 2;
		bad.z = 3;
		preset.randomSpawnPositions.add(good);
		preset.randomSpawnPositions.add(bad);

		preset.normalize();

		assertEquals(1, preset.randomSpawnPositions.size());
		assertEquals(90, preset.randomSpawnPositions.get(0).pitch);
	}

	@Test
	void normalizeSnapsRandomSpawnCoordinatesToBlockCenters() {
		MapPreset preset = new MapPreset();
		preset.randomSpawnPositions = new ArrayList<>();
		MapPreset.PosData spawn = new MapPreset.PosData();
		spawn.x = 12.23;
		spawn.y = 65.0;
		spawn.z = -4.8;
		spawn.yaw = 10;
		spawn.pitch = 20;
		preset.randomSpawnPositions.add(spawn);

		preset.normalize();

		MapPreset.PosData normalized = preset.randomSpawnPositions.get(0);
		assertEquals(12.5, normalized.x);
		assertEquals(65.0, normalized.y);
		assertEquals(-4.5, normalized.z);
	}

	@Test
	void presetNamesStayFilesystemSafe() {
		assertFalse(PresetStorage.isValidName("../bad"));
		assertFalse(PresetStorage.isValidName("bad/name"));
		assertFalse(PresetStorage.isValidName(""));
		assertFalse(PresetStorage.isValidName(null));
	}

	@Test
	void recalculatesTrainOffsetFromActualBoxes() {
		MapPreset preset = new MapPreset();
		preset.playAreaMode = MapPreset.PlayAreaMode.TRAIN;
		preset.resetTemplateArea = box(10, 20, 30, 20, 30, 40);
		preset.playArea = box(110, 70, -20, 120, 80, -10);

		preset.recalculateDerivedAreas();

		assertEquals(100, preset.playAreaOffset.x);
		assertEquals(50, preset.playAreaOffset.y);
		assertEquals(-50, preset.playAreaOffset.z);
	}

	@Test
	void fullStaticMapUsesWholeMapGeometry() {
		MapPreset preset = new MapPreset();
		preset.terrainMode = MapPreset.TerrainMode.STATIC;
		preset.playAreaMode = MapPreset.PlayAreaMode.FULL_MAP;
		preset.wholeMapArea = box(-5, 1, -8, 15, 12, 20);

		preset.recalculateDerivedAreas();

		assertTrue(preset.isStaticMapEnabled());
		assertEquals(-5, preset.playArea.minX);
		assertEquals(20, preset.resetTemplateArea.maxZ);
		assertEquals(0, preset.playAreaOffset.x);
	}

	@Test
	void validatorReportsRequiredMapSetupErrors() {
		List<MapPresetValidator.Issue> issues = MapPresetValidator.validate(null, "empty", new MapPreset());

		assertTrue(hasIssue(issues, MapPresetValidator.Severity.ERROR, "Missing whole map area."));
		assertTrue(hasIssue(issues, MapPresetValidator.Severity.ERROR, "Missing play area."));
		assertTrue(hasIssue(issues, MapPresetValidator.Severity.ERROR,
			"Train play-area mode requires a default train preset."));
	}

	@Test
	void validatorAcceptsCompleteStaticFullMapSetup() {
		MapPreset preset = new MapPreset();
		preset.terrainMode = MapPreset.TerrainMode.STATIC;
		preset.playAreaMode = MapPreset.PlayAreaMode.FULL_MAP;
		preset.wholeMapArea = box(-8, 60, -8, 8, 90, 8);
		preset.playArea = box(-8, 60, -8, 8, 90, 8);
		preset.resetTemplateArea = box(-8, 60, -8, 8, 90, 8);
		preset.playAreaOffset = new MapPreset.OffsetData();
		preset.spectatorSpawnPos = pos(0, 80, 0);
		preset.readyAreaSpawnPos = pos(0, 65, 0);
		preset.randomSpawnPositions = new ArrayList<>();
		preset.randomSpawnPositions.add(pos(0, 65, 0));
		preset.freshAirAreas = new ArrayList<>();
		MapPreset.FreshAirAreaData freshAir = new MapPreset.FreshAirAreaData();
		freshAir.area = box(-4, 64, -4, 4, 72, 4);
		preset.freshAirAreas.add(freshAir);

		List<MapPresetValidator.Issue> issues = MapPresetValidator.validate(null, "static_full", preset);

		assertTrue(issues.isEmpty());
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

	private static MapPreset.PosData pos(double x, double y, double z) {
		MapPreset.PosData pos = new MapPreset.PosData();
		pos.x = x;
		pos.y = y;
		pos.z = z;
		return pos;
	}

	private static boolean hasIssue(List<MapPresetValidator.Issue> issues,
			MapPresetValidator.Severity severity, String message) {
		return issues.stream().anyMatch(issue -> issue.severity() == severity && issue.message().equals(message));
	}
}
