package dev.mapselect.config;

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
import java.util.LinkedHashMap;
import java.util.Map;

public final class RoleModifierTuningConfig {
	public static final int DEFAULT_CHANCE = 100;
	public static final int DEFAULT_MAX = 1;
	public static final int CHANCE_MIN = 0;
	public static final int CHANCE_MAX = 100;
	public static final int MAX_MIN = 0;
	public static final int MAX_MAX = 64;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("gexpress_role_modifier_tuning.json");

	private static final Map<String, Integer> roleChance = new LinkedHashMap<>();
	private static final Map<String, Integer> roleMax = new LinkedHashMap<>();
	private static final Map<String, Integer> modifierChance = new LinkedHashMap<>();
	private static final Map<String, Integer> modifierMax = new LinkedHashMap<>();

	private RoleModifierTuningConfig() {}

	public static void load() {
		try {
			if (!Files.exists(CONFIG_PATH)) {
				save();
				return;
			}
			Snapshot snap = GSON.fromJson(Files.readString(CONFIG_PATH), Snapshot.class);
			if (snap == null) return;
			replace(roleChance, snap.roleChance, CHANCE_MIN, CHANCE_MAX);
			replace(roleMax, snap.roleMax, MAX_MIN, MAX_MAX);
			replace(modifierChance, snap.modifierChance, CHANCE_MIN, CHANCE_MAX);
			replace(modifierMax, snap.modifierMax, MAX_MIN, MAX_MAX);
		} catch (IOException | JsonSyntaxException e) {
			MapSelect.LOGGER.warn("Failed to load gexpress_role_modifier_tuning.json; keeping defaults.", e);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Snapshot snap = new Snapshot();
			snap.roleChance.putAll(roleChance);
			snap.roleMax.putAll(roleMax);
			snap.modifierChance.putAll(modifierChance);
			snap.modifierMax.putAll(modifierMax);
			writeAtomically(CONFIG_PATH, GSON.toJson(snap));
		} catch (IOException e) {
			MapSelect.LOGGER.warn("Failed to save gexpress_role_modifier_tuning.json.", e);
		}
	}

	public static int getRoleChance(String id) {
		return roleChance.getOrDefault(id, DEFAULT_CHANCE);
	}

	public static int getRoleMax(String id) {
		return roleMax.getOrDefault(id, DEFAULT_MAX);
	}

	public static int getModifierChance(String id) {
		return modifierChance.getOrDefault(id, DEFAULT_CHANCE);
	}

	public static int getModifierMax(String id) {
		return modifierMax.getOrDefault(id, DEFAULT_MAX);
	}

	public static void setRoleChance(String id, int value) {
		put(roleChance, id, value, CHANCE_MIN, CHANCE_MAX);
	}

	public static void setRoleMax(String id, int value) {
		put(roleMax, id, value, MAX_MIN, MAX_MAX);
	}

	public static void setModifierChance(String id, int value) {
		put(modifierChance, id, value, CHANCE_MIN, CHANCE_MAX);
	}

	public static void setModifierMax(String id, int value) {
		put(modifierMax, id, value, MAX_MIN, MAX_MAX);
	}

	public static Map<String, Integer> roleChanceEntries() {
		return Map.copyOf(roleChance);
	}

	public static Map<String, Integer> roleMaxEntries() {
		return Map.copyOf(roleMax);
	}

	public static Map<String, Integer> modifierChanceEntries() {
		return Map.copyOf(modifierChance);
	}

	public static Map<String, Integer> modifierMaxEntries() {
		return Map.copyOf(modifierMax);
	}

	private static void replace(Map<String, Integer> target, Map<String, Integer> source, int min, int max) {
		target.clear();
		if (source == null) return;
		for (Map.Entry<String, Integer> entry : source.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) continue;
			put(target, entry.getKey(), entry.getValue(), min, max);
		}
	}

	private static void put(Map<String, Integer> target, String id, int value, int min, int max) {
		if (id == null || id.isBlank()) return;
		target.put(id, Math.max(min, Math.min(max, value)));
	}

	private static void writeAtomically(Path target, String json) throws IOException {
		Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		try {
			Files.writeString(tmp, json);
			try {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private static final class Snapshot {
		Map<String, Integer> roleChance = new LinkedHashMap<>();
		Map<String, Integer> roleMax = new LinkedHashMap<>();
		Map<String, Integer> modifierChance = new LinkedHashMap<>();
		Map<String, Integer> modifierMax = new LinkedHashMap<>();
	}
}
