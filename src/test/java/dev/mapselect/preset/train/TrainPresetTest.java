package dev.mapselect.preset.train;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainPresetTest {
	@Test
	void normalizeDropsInvalidSlotsAndClampsPitch() {
		TrainPreset preset = new TrainPreset();
		preset.teleportationSlots = new ArrayList<>();

		TrainPreset.SlotData good = new TrainPreset.SlotData();
		good.id = 4;
		good.x = 1.0;
		good.y = 2.0;
		good.z = 3.0;
		good.yaw = 20.0F;
		good.pitch = 120.0F;
		preset.teleportationSlots.add(good);

		TrainPreset.SlotData bad = new TrainPreset.SlotData();
		bad.id = 5;
		bad.x = Double.POSITIVE_INFINITY;
		bad.y = 2.0;
		bad.z = 3.0;
		preset.teleportationSlots.add(bad);

		preset.normalize();

		assertEquals(1, preset.teleportationSlots.size());
		assertEquals(4, preset.teleportationSlots.get(0).id);
		assertEquals(90.0F, preset.teleportationSlots.get(0).pitch);
	}

	@Test
	void normalizeCapsSlotCount() {
		TrainPreset preset = new TrainPreset();
		preset.teleportationSlots = new ArrayList<>();
		for (int i = 0; i < 300; i++) {
			TrainPreset.SlotData slot = new TrainPreset.SlotData();
			slot.id = i;
			slot.x = i;
			slot.y = 64.0;
			slot.z = -i;
			preset.teleportationSlots.add(slot);
		}

		preset.normalize();

		assertEquals(256, preset.teleportationSlots.size());
	}
}
