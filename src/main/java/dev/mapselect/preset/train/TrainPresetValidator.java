package dev.mapselect.preset.train;

import dev.mapselect.preset.map.MapPreset;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TrainPresetValidator {
	private TrainPresetValidator() {}

	public static List<Issue> validate(String name, TrainPreset preset) {
		List<Issue> issues = new ArrayList<>();
		if (preset == null) {
			issues.add(Issue.error("Train preset is missing or could not be loaded."));
			return List.copyOf(issues);
		}
		preset.normalize();
		if (preset.resetTemplateArea == null) issues.add(Issue.error("Missing reset template area."));
		if (preset.resetPasteOffset == null) issues.add(Issue.warning("Missing reset paste offset."));
		if (preset.slotCount() == 0) {
			issues.add(Issue.warning("No teleportation slots are saved."));
		} else {
			int duplicateIds = duplicateSlotIds(preset.teleportationSlots);
			if (duplicateIds > 0) issues.add(Issue.warning(duplicateIds + " teleportation slot id(s) are duplicated."));
			int duplicateBlocks = duplicateSlotBlocks(preset.teleportationSlots);
			if (duplicateBlocks > 0) issues.add(Issue.warning(duplicateBlocks + " teleportation slot(s) share a block with another slot."));
		}
		if (preset.cartCount() == 0) {
			issues.add(Issue.warning("No train cart regions are saved."));
		}
		if (preset.resetTemplateArea != null && preset.trainCarts != null) {
			long outside = preset.trainCarts.stream()
				.filter(cart -> cart != null && cart.area != null && !contains(preset.resetTemplateArea, cart.area))
				.count();
			if (outside > 0) issues.add(Issue.warning(outside + " train cart region(s) are outside the reset template area."));
		}
		return List.copyOf(issues);
	}

	private static int duplicateSlotIds(List<TrainPreset.SlotData> slots) {
		Set<Integer> seen = new HashSet<>();
		int duplicates = 0;
		for (TrainPreset.SlotData slot : slots) {
			if (slot == null) continue;
			if (!seen.add(slot.id)) duplicates++;
		}
		return duplicates;
	}

	private static int duplicateSlotBlocks(List<TrainPreset.SlotData> slots) {
		Set<String> seen = new HashSet<>();
		int duplicates = 0;
		for (TrainPreset.SlotData slot : slots) {
			if (slot == null) continue;
			String key = (int) Math.floor(slot.x) + "," + (int) Math.floor(slot.y) + "," + (int) Math.floor(slot.z);
			if (!seen.add(key)) duplicates++;
		}
		return duplicates;
	}

	private static boolean contains(MapPreset.BoxData outer, MapPreset.BoxData inner) {
		return inner.minX >= outer.minX && inner.maxX <= outer.maxX
			&& inner.minY >= outer.minY && inner.maxY <= outer.maxY
			&& inner.minZ >= outer.minZ && inner.maxZ <= outer.maxZ;
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
