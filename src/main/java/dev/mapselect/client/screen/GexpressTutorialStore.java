package dev.mapselect.client.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.mapselect.MapSelect;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class GexpressTutorialStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("gexpress_tutorial.json");
	private static Snapshot snapshot;

	private GexpressTutorialStore() {}

	public static boolean enabled() {
		load();
		return snapshot.enabled;
	}

	public static void setEnabled(boolean enabled) {
		load();
		snapshot.enabled = enabled;
		save();
	}

	public static boolean shouldOpen() {
		load();
		return snapshot.enabled && !snapshot.seen && !pages().isEmpty();
	}

	public static void markSeen() {
		load();
		snapshot.seen = true;
		save();
	}

	public static void resetSeen() {
		load();
		snapshot.seen = false;
		save();
	}

	public static List<Page> pages() {
		load();
		List<Page> pages = new ArrayList<>();
		for (Page page : snapshot.pages) {
			if (page == null) continue;
			String title = clean(page.title, "Tutorial");
			String body = clean(page.body, "");
			String image = clean(page.image, "");
			if (!title.isBlank() || !body.isBlank() || !image.isBlank()) {
				pages.add(new Page(title, body, image));
			}
		}
		return pages;
	}

	public static void setPages(List<Page> pages) {
		load();
		snapshot.pages = new ArrayList<>();
		for (Page page : pages) {
			if (page == null) continue;
			snapshot.pages.add(new Page(clean(page.title, "Tutorial"), clean(page.body, ""), clean(page.image, "")));
		}
		if (snapshot.pages.isEmpty()) snapshot.pages.addAll(defaultPages());
		save();
	}

	private static void load() {
		if (snapshot != null) return;
		if (Files.isRegularFile(PATH)) {
			try {
				Snapshot loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), Snapshot.class);
				snapshot = loaded == null ? new Snapshot() : loaded;
			} catch (IOException | JsonSyntaxException e) {
				MapSelect.LOGGER.warn("Failed to load G'Express tutorial config: {}", e.toString());
				snapshot = new Snapshot();
			}
		} else {
			snapshot = new Snapshot();
			save();
		}
		if (snapshot.pages == null || snapshot.pages.isEmpty()) snapshot.pages = defaultPages();
	}

	private static void save() {
		if (snapshot == null) return;
		try {
			Files.createDirectories(PATH.getParent());
			Path tmp = Files.createTempFile(PATH.getParent(), PATH.getFileName().toString(), ".tmp");
			try {
				Files.writeString(tmp, GSON.toJson(snapshot), StandardCharsets.UTF_8);
				try {
					Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (AtomicMoveNotSupportedException ignored) {
					Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				Files.deleteIfExists(tmp);
			}
		} catch (IOException e) {
			MapSelect.LOGGER.warn("Failed to save G'Express tutorial config: {}", e.toString());
		}
	}

	private static List<Page> defaultPages() {
		List<Page> pages = new ArrayList<>();
		pages.add(new Page("Welcome to G'Express",
			"Open the menu to check skins, XP rewards, bug reports, and game settings. This tutorial only appears once unless a dev resets it.",
			"gexpress:textures/gui/gexpress_mini.png"));
		pages.add(new Page("During A Game",
			"Use your role item, watch the ability bars, and check the guidebook when you need role details.",
			""));
		return pages;
	}

	private static String clean(String value, String fallback) {
		return value == null ? fallback : value.strip();
	}

	private static final class Snapshot {
		boolean enabled = true;
		boolean seen = false;
		List<Page> pages = defaultPages();
	}

	public record Page(String title, String body, String image) {}
}
