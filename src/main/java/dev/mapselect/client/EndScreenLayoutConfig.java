package dev.mapselect.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.mapselect.MapSelect;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EndScreenLayoutConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("gexpress_end_screen_layout.json");
	private static Layout layout = load();

	private EndScreenLayoutConfig() {}

	public static Section civilians() {
		return layout.civilians;
	}

	public static Section vigilantes() {
		return layout.vigilantes;
	}

	public static Section neutrals() {
		return layout.neutrals;
	}

	public static Section killers() {
		return layout.killers;
	}

	public static Section mafia() {
		return layout.mafia;
	}

	public static void set(Kind kind, int x, int y) {
		Section section = section(kind);
		section.x = snap(x);
		section.y = snap(y);
		save();
	}

	public static void reset() {
		layout = defaults();
		save();
	}

	public static Section section(Kind kind) {
		return switch (kind) {
			case CIVILIANS -> layout.civilians;
			case VIGILANTES -> layout.vigilantes;
			case NEUTRALS -> layout.neutrals;
			case KILLERS -> layout.killers;
			case MAFIA -> layout.mafia;
		};
	}

	private static Layout load() {
		if (Files.exists(PATH)) {
			try {
				Layout loaded = GSON.fromJson(Files.readString(PATH), Layout.class);
				if (loaded != null) return normalize(loaded);
			} catch (Exception e) {
				MapSelect.LOGGER.warn("Failed to load G'Express end screen layout: {}", e.toString());
			}
		}
		return defaults();
	}

	private static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(layout));
		} catch (IOException e) {
			MapSelect.LOGGER.warn("Failed to save G'Express end screen layout: {}", e.toString());
		}
	}

	private static Layout normalize(Layout value) {
		Layout normalized = defaults();
		normalized.civilians = normalize(value.civilians, normalized.civilians);
		normalized.vigilantes = normalize(value.vigilantes, normalized.vigilantes);
		normalized.neutrals = normalize(value.neutrals, normalized.neutrals);
		normalized.killers = normalize(value.killers, normalized.killers);
		normalized.mafia = normalize(value.mafia, normalized.mafia);
		return normalized;
	}

	private static Section normalize(Section value, Section fallback) {
		if (value == null) return fallback;
		Section out = new Section();
		out.x = clamp(snap(value.x), -180, 180);
		out.y = clamp(snap(value.y), -20, 150);
		out.columns = clamp(value.columns <= 0 ? fallback.columns : value.columns, 1, 6);
		return out;
	}

	private static int snap(int value) {
		return Math.round(value / 4.0F) * 4;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Layout defaults() {
		Layout value = new Layout();
		value.civilians = section(-120, 14, 4);
		value.vigilantes = section(-48, 14, 2);
		value.neutrals = section(16, 14, 2);
		value.killers = section(80, 14, 2);
		value.mafia = section(32, 58, 2);
		return value;
	}

	private static Section section(int x, int y, int columns) {
		Section section = new Section();
		section.x = x;
		section.y = y;
		section.columns = columns;
		return section;
	}

	public enum Kind {
		CIVILIANS,
		VIGILANTES,
		NEUTRALS,
		KILLERS,
		MAFIA
	}

	public static final class Layout {
		public Section civilians;
		public Section vigilantes;
		public Section neutrals;
		public Section killers;
		public Section mafia;
	}

	public static final class Section {
		public int x;
		public int y;
		public int columns;
	}
}
