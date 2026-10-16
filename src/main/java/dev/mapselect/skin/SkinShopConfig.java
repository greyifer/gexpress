package dev.mapselect.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.mapselect.MapSelect;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-owned presentation, rotation, visibility, and pricing for the skin store. */
public final class SkinShopConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("gexpress-skin-shop.json");
	private static Document current = defaults();

	private SkinShopConfig() {}

	public static synchronized void load() {
		try {
			if (!Files.exists(CONFIG_PATH)) {
				current = defaults();
				save();
				return;
			}
			current = normalize(parse(Files.readString(CONFIG_PATH)));
			save();
		} catch (IOException | IllegalArgumentException e) {
			MapSelect.LOGGER.warn("Failed to load {}. Keeping the last valid skin-shop settings.",
				CONFIG_PATH.getFileName(), e);
		}
	}

	public static synchronized void save() throws IOException {
		writeAtomically(CONFIG_PATH, GSON.toJson(current));
	}

	public static synchronized String json() {
		return GSON.toJson(current);
	}

	public static synchronized void replaceJson(String json, boolean persist) throws IOException {
		Document replacement;
		try {
			replacement = normalize(parse(json));
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage(), e);
		}
		if (persist) writeAtomically(CONFIG_PATH, GSON.toJson(replacement));
		current = replacement;
	}

	/** Applies a server packet on clients without writing the server's settings into local config. */
	public static synchronized void applySyncedJson(String json) {
		try {
			current = normalize(parse(json));
		} catch (IllegalArgumentException e) {
			MapSelect.LOGGER.warn("Ignored malformed skin-shop sync: {}", e.getMessage());
		}
	}

	public static synchronized boolean casesEnabled() {
		return current.casesEnabled;
	}

	public static synchronized boolean dailyOffersEnabled() {
		return current.dailyOffersEnabled;
	}

	public static synchronized int offerCount() {
		return current.offerCount;
	}

	public static synchronized int rotationHours() {
		return current.rotationHours;
	}

	public static synchronized String title() {
		return current.title;
	}

	public static synchronized String subtitle() {
		return current.subtitle;
	}

	public static synchronized String style() {
		return current.style;
	}

	public static synchronized int accentArgb() {
		return 0xFF000000 | parseColor(current.accentColor, 0xFFD166);
	}

	public static synchronized int panelArgb(int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | parseColor(current.panelColor, 0x272E36);
	}

	public static synchronized List<SkinRule> enabledSkins() {
		return current.skins.stream().filter(SkinRule::enabled).toList();
	}

	public static synchronized SkinRule rule(WeaponSkinType type, WeaponSkin skin) {
		if (type == null || skin == null) return null;
		String wantedType = type.id();
		String wantedSkin = skin.logical(type).id();
		for (SkinRule rule : current.skins) {
			if (rule.type().equals(wantedType) && rule.skin().equals(wantedSkin)) return rule;
		}
		return null;
	}

	public static boolean isEligibleSkin(WeaponSkinType type, WeaponSkin skin) {
		if (type == null || skin == null || !skin.supports(type) || !skin.visibleInPicker(type)) return false;
		if (skin == WeaponSkin.DEFAULT || skin.unlockedByDefault()) return false;
		return skin != WeaponSkin.HOST
			&& skin != WeaponSkin.TRUSTED
			&& skin != WeaponSkin.DEV
			&& skin != WeaponSkin.PASSENGER;
	}

	private static Document parse(String json) {
		if (json == null || json.isBlank()) throw new IllegalArgumentException("Skin-shop JSON is empty.");
		try {
			Document parsed = GSON.fromJson(json, Document.class);
			if (parsed == null) throw new IllegalArgumentException("Skin-shop JSON is empty.");
			return parsed;
		} catch (JsonSyntaxException e) {
			throw new IllegalArgumentException("Malformed skin-shop JSON: " + e.getMessage(), e);
		}
	}

	private static Document normalize(Document source) {
		Document normalized = new Document();
		normalized.casesEnabled = source.casesEnabled;
		normalized.dailyOffersEnabled = source.dailyOffersEnabled;
		normalized.offerCount = Math.max(1, Math.min(8, source.offerCount));
		normalized.rotationHours = Math.max(1, Math.min(168, source.rotationHours));
		normalized.title = cleanText(source.title, "G'Express Store", 64);
		normalized.subtitle = cleanText(source.subtitle,
			"Featured cases and personal daily offers.", 140);
		String style = cleanText(source.style, "valorant", 16).toLowerCase(Locale.ROOT);
		normalized.style = "compact".equals(style) ? "compact" : "valorant";
		normalized.accentColor = formatColor(parseColor(source.accentColor, 0xFFD166));
		normalized.panelColor = formatColor(parseColor(source.panelColor, 0x272E36));

		Map<String, SkinRule> supplied = new LinkedHashMap<>();
		if (source.skins != null) {
			for (SkinRule raw : source.skins) {
				SkinRule rule = normalizeRule(raw);
				if (rule != null) supplied.put(rule.type() + ":" + rule.skin(), rule);
			}
		}
		normalized.skins = new ArrayList<>();
		for (SkinRule fallback : defaultRules()) {
			normalized.skins.add(supplied.getOrDefault(
				fallback.type() + ":" + fallback.skin(), fallback));
		}
		return normalized;
	}

	private static SkinRule normalizeRule(SkinRule raw) {
		if (raw == null) return null;
		WeaponSkinType type = WeaponSkinType.byId(raw.type());
		WeaponSkin skin = WeaponSkin.byId(raw.skin());
		if (type == null || skin == null) return null;
		skin = skin.logical(type);
		if (!isEligibleSkin(type, skin)) return null;
		return new SkinRule(type.id(), skin.id(), Math.max(0, Math.min(1_000_000, raw.price())), raw.enabled());
	}

	private static Document defaults() {
		Document document = new Document();
		document.skins = defaultRules();
		return document;
	}

	private static List<SkinRule> defaultRules() {
		Map<String, SkinRule> rules = new LinkedHashMap<>();
		for (WeaponSkinType type : WeaponSkinType.values()) {
			for (WeaponSkin registered : WeaponSkin.values()) {
				WeaponSkin skin = registered.logical(type);
				if (!isEligibleSkin(type, skin)) continue;
				String key = type.id() + ":" + skin.id();
				rules.putIfAbsent(key, new SkinRule(type.id(), skin.id(), defaultPrice(type, skin), true));
			}
		}
		return new ArrayList<>(rules.values());
	}

	private static int defaultPrice(WeaponSkinType type, WeaponSkin skin) {
		int band = Math.floorMod(java.util.Objects.hash(type.id(), skin.id()), 8);
		return (type == WeaponSkinType.KNIFE ? 850 : 500) + band * 75;
	}

	private static String cleanText(String value, String fallback, int maxLength) {
		String cleaned = value == null ? "" : value.strip();
		if (cleaned.isEmpty()) cleaned = fallback;
		return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
	}

	private static int parseColor(String value, int fallback) {
		if (value == null) return fallback;
		String cleaned = value.strip();
		if (cleaned.startsWith("#")) cleaned = cleaned.substring(1);
		if (cleaned.length() != 6) return fallback;
		try {
			return Integer.parseInt(cleaned, 16) & 0xFFFFFF;
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String formatColor(int rgb) {
		return "#%06X".formatted(rgb & 0xFFFFFF);
	}

	private static void writeAtomically(Path target, String json) throws IOException {
		Files.createDirectories(target.getParent());
		Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		try {
			Files.writeString(temporary, json);
			try {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public record SkinRule(String type, String skin, int price, boolean enabled) {}

	private static final class Document {
		boolean casesEnabled = true;
		boolean dailyOffersEnabled = true;
		int offerCount = 4;
		int rotationHours = 24;
		String title = "G'Express Store";
		String subtitle = "Featured cases and personal daily offers.";
		String style = "valorant";
		String accentColor = "#FFD166";
		String panelColor = "#272E36";
		List<SkinRule> skins = new ArrayList<>();
	}
}
