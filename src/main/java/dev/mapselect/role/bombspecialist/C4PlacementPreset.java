package dev.mapselect.role.bombspecialist;

import java.util.Locale;
import java.util.UUID;

public record C4PlacementPreset(float offsetX, float offsetY, float offsetZ,
		float rotationX, float rotationY, float rotationZ, float slant, float scale) {
	public static final C4PlacementPreset DEFAULT =
		new C4PlacementPreset(0.0F, 0.24F, 0.28F, 0.0F, 0.0F, 0.0F, 0.0F, 0.42F);

	public static C4PlacementPreset parse(String raw) {
		if (raw == null) return null;
		String normalized = raw.trim().replace(',', ' ');
		if (normalized.isEmpty()) return null;
		String[] parts = normalized.split("\\s+");
		if (parts.length != 8) return null;
		try {
			return new C4PlacementPreset(
				Float.parseFloat(parts[0]),
				Float.parseFloat(parts[1]),
				Float.parseFloat(parts[2]),
				Float.parseFloat(parts[3]),
				Float.parseFloat(parts[4]),
				Float.parseFloat(parts[5]),
				Float.parseFloat(parts[6]),
				Float.parseFloat(parts[7])
			).clamped();
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	public C4PlacementPreset clamped() {
		return new C4PlacementPreset(
			clamp(offsetX, -1.0F, 1.0F, DEFAULT.offsetX),
			clamp(offsetY, -1.0F, 1.0F, DEFAULT.offsetY),
			clamp(offsetZ, -1.0F, 1.0F, DEFAULT.offsetZ),
			clamp(rotationX, -180.0F, 180.0F, DEFAULT.rotationX),
			clamp(rotationY, -180.0F, 180.0F, DEFAULT.rotationY),
			clamp(rotationZ, -180.0F, 180.0F, DEFAULT.rotationZ),
			clamp(slant, -180.0F, 180.0F, DEFAULT.slant),
			clamp(scale, 0.05F, 2.0F, DEFAULT.scale)
		);
	}

	public String toConfigString() {
		return String.format(Locale.ROOT, "%.3f %.3f %.3f %.3f %.3f %.3f %.3f %.3f",
			offsetX, offsetY, offsetZ, rotationX, rotationY, rotationZ, slant, scale);
	}

	public static int indexFor(UUID uuid, int presetCount) {
		if (presetCount <= 1) return 0;
		if (uuid == null) return 0;
		return Math.floorMod(uuid.hashCode(), presetCount);
	}

	private static float clamp(float value, float min, float max, float fallback) {
		if (!Float.isFinite(value)) return fallback;
		return Math.max(min, Math.min(max, value));
	}
}
