package dev.mapselect.skin;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class WeaponSkin {
	private static final Map<String, WeaponSkin> REGISTRY = new LinkedHashMap<>();

	public static final WeaponSkin DEFAULT = builtIn("default", "Default", 0x8EA1AD, true,
		Set.of(WeaponSkinType.KNIFE, WeaponSkinType.GUN));
	public static final WeaponSkin HOST = builtIn("host", "Host", 0x559CFF, false,
		Set.of(WeaponSkinType.KNIFE, WeaponSkinType.GUN));
	public static final WeaponSkin TRUSTED = builtIn("trusted", "Trusted", 0xF2C94C, false,
		Set.of(WeaponSkinType.KNIFE, WeaponSkinType.GUN));
	public static final WeaponSkin DEV = builtIn("dev", "Dev", 0xCBFF2E, false,
		Set.of(WeaponSkinType.KNIFE, WeaponSkinType.GUN));
	public static final WeaponSkin PASSENGER = builtIn("passenger", "Default", 0x8EA1AD, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin COLA = builtIn("cola", "Cola", 0xE63B34, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin GOLD = builtIn("gold", "Gold", 0xF2C94C, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin JEM = builtIn("jem", "Jem", 0x82D97A, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin BLUE = builtIn("blue", "Blue", 0x4AA8FF, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin PINK = builtIn("pink", "Pink", 0xFF70C8, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin PLAID = builtIn("plaid", "Plaid", 0xD8C1A2, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin BAMBOO = builtIn("bamboo", "Bamboo", 0x8BDE78, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin ATOMIZER = builtIn("atomizer", "Atomizer", 0x9B74FF, false,
		Set.of(WeaponSkinType.GUN));

	private final String id;
	private String displayName;
	private final int color;
	private final boolean unlockedByDefault;
	private final Set<WeaponSkinType> types;
	private final Map<WeaponSkinType, Identifier> models;
	private final boolean imported;

	private WeaponSkin(String id, String displayName, int color, boolean unlockedByDefault,
			Set<WeaponSkinType> types, Map<WeaponSkinType, Identifier> models, boolean imported) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.unlockedByDefault = unlockedByDefault;
		this.types = Set.copyOf(types);
		this.models = Map.copyOf(models);
		this.imported = imported;
	}

	private static WeaponSkin builtIn(String id, String displayName, int color, boolean unlockedByDefault,
			Set<WeaponSkinType> types) {
		WeaponSkin skin = new WeaponSkin(id, displayName, color, unlockedByDefault, types, Map.of(), false);
		REGISTRY.put(id, skin);
		return skin;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public int color() {
		return color;
	}

	public boolean unlockedByDefault() {
		return unlockedByDefault;
	}

	public boolean imported() {
		return imported;
	}

	public Identifier model(WeaponSkinType type) {
		return type == null ? null : models.get(type);
	}

	public boolean supports(WeaponSkinType type) {
		return type != null && types.contains(type);
	}

	public WeaponSkin logical(WeaponSkinType type) {
		if (type == WeaponSkinType.GUN) {
			if (this == PASSENGER) return DEFAULT;
			if (this == HOST) return GOLD;
			if (this == TRUSTED) return COLA;
		}
		return this;
	}

	public boolean visibleInPicker(WeaponSkinType type) {
		if (!supports(type)) return false;
		return type != WeaponSkinType.GUN || (this != PASSENGER && this != HOST && this != TRUSTED);
	}

	public static synchronized WeaponSkin byId(String raw) {
		if (raw == null) return null;
		return REGISTRY.get(normalize(raw));
	}

	public static synchronized WeaponSkin[] values() {
		return REGISTRY.values().toArray(WeaponSkin[]::new);
	}

	public static synchronized void replaceImported(Collection<ImportedDefinition> definitions) {
		REGISTRY.entrySet().removeIf(entry -> entry.getValue().imported);
		if (definitions == null) return;
		for (ImportedDefinition definition : definitions) {
			if (definition == null || definition.type() == null || definition.model() == null) continue;
			String id = normalize(definition.id());
			if (id.isBlank() || REGISTRY.containsKey(id)) continue;
			String displayName = definition.displayName() == null || definition.displayName().isBlank()
				? title(id) : definition.displayName().strip();
			REGISTRY.put(id, new WeaponSkin(id, displayName, definition.color(), false,
				Set.of(definition.type()), Map.of(definition.type(), definition.model()), true));
		}
	}

	public static synchronized List<ImportedDefinition> importedDefinitions() {
		List<ImportedDefinition> definitions = new ArrayList<>();
		for (WeaponSkin skin : REGISTRY.values()) {
			if (!skin.imported) continue;
			for (Map.Entry<WeaponSkinType, Identifier> model : skin.models.entrySet()) {
				definitions.add(new ImportedDefinition(skin.id, skin.displayName, skin.color, model.getKey(), model.getValue()));
			}
		}
		return List.copyOf(definitions);
	}

	public static synchronized void applyDisplayNames(Map<String, String> names) {
		if (names == null) return;
		for (Map.Entry<String, String> entry : names.entrySet()) {
			WeaponSkin skin = REGISTRY.get(normalize(entry.getKey()));
			String name = entry.getValue() == null ? "" : entry.getValue().strip();
			if (skin != null && !name.isBlank()) skin.displayName = name;
		}
	}

	private static String normalize(String raw) {
		return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
	}

	private static String title(String id) {
		String clean = id.replaceFirst("^(knife|revolver|gun)[_-]", "").replace('_', ' ').replace('-', ' ').strip();
		if (clean.isEmpty()) return id;
		StringBuilder out = new StringBuilder(clean.length());
		boolean upper = true;
		for (char c : clean.toCharArray()) {
			out.append(upper ? Character.toUpperCase(c) : c);
			upper = c == ' ';
		}
		return out.toString();
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof WeaponSkin skin && id.equals(skin.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	public record ImportedDefinition(String id, String displayName, int color, WeaponSkinType type,
	                                 Identifier model) {}
}
