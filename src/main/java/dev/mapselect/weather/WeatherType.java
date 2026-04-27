package dev.mapselect.weather;

public enum WeatherType {
	NONE,
	SNOW,
	SANDSTORM;

	public static WeatherType fromString(String s) {
		if (s == null || s.isEmpty()) return NONE;
		try {
			return WeatherType.valueOf(s.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return NONE;
		}
	}
}
