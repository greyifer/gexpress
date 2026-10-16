package dev.mapselect.config;

final class ConfigBounds {
	private ConfigBounds() {}

	static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	static float clamp(float value, float min, float max, float fallback) {
		if (!Float.isFinite(value)) return fallback;
		return Math.max(min, Math.min(max, value));
	}

	static int parseInt(String raw, int fallback, int min, int max) {
		if (raw == null) return fallback;
		try {
			return clamp(Integer.parseInt(raw.strip()), min, max);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}
}
