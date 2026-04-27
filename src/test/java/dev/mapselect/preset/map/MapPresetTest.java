package dev.mapselect.preset.map;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
