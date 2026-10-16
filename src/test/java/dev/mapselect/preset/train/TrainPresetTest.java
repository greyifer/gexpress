package dev.mapselect.preset.train;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	void validatorReportsRiskyTrainPresetSetup() {
		TrainPreset preset = new TrainPreset();
		preset.resetTemplateArea = box(0, 60, 0, 10, 80, 10);
		preset.teleportationSlots = new ArrayList<>();
		preset.teleportationSlots.add(slot(1, 2.5, 65, 2.5));
		preset.teleportationSlots.add(slot(1, 2.5, 65, 2.5));
		preset.trainCarts = new ArrayList<>();
		TrainPreset.CartData cart = new TrainPreset.CartData();
		cart.area = box(20, 60, 20, 25, 70, 25);
		preset.trainCarts.add(cart);

		List<TrainPresetValidator.Issue> issues = TrainPresetValidator.validate("risky", preset);

		assertTrue(hasIssue(issues, TrainPresetValidator.Severity.WARNING,
			"Missing reset paste offset."));
		assertTrue(hasIssue(issues, TrainPresetValidator.Severity.WARNING,
			"1 teleportation slot id(s) are duplicated."));
		assertTrue(hasIssue(issues, TrainPresetValidator.Severity.WARNING,
			"1 teleportation slot(s) share a block with another slot."));
		assertTrue(hasIssue(issues, TrainPresetValidator.Severity.WARNING,
			"1 train cart region(s) are outside the reset template area."));
	}

	private static TrainPreset.SlotData slot(int id, double x, double y, double z) {
		TrainPreset.SlotData slot = new TrainPreset.SlotData();
		slot.id = id;
		slot.x = x;
		slot.y = y;
		slot.z = z;
		return slot;
	}

	private static dev.mapselect.preset.map.MapPreset.BoxData box(double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ) {
		dev.mapselect.preset.map.MapPreset.BoxData box = new dev.mapselect.preset.map.MapPreset.BoxData();
		box.minX = minX;
		box.minY = minY;
		box.minZ = minZ;
		box.maxX = maxX;
		box.maxY = maxY;
		box.maxZ = maxZ;
		return box;
	}

	private static boolean hasIssue(List<TrainPresetValidator.Issue> issues,
			TrainPresetValidator.Severity severity, String message) {
		return issues.stream().anyMatch(issue -> issue.severity() == severity && issue.message().equals(message));
	}
}
