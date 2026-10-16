package dev.mapselect.skin;

import java.util.LinkedHashMap;
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
	public static final WeaponSkin SHOTGUN = builtIn("shotgun", "Shotgun", 0x56616D, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin SHOTGUN_BRONZE = builtIn("shotgun_bronze", "Bronze Shotgun", 0xB66A16, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin SHOTGUN_CREEPER = builtIn("shotgun_creeper", "Creeper Shotgun", 0x36D968, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin SHOTGUN_PURPLE = builtIn("shotgun_purple", "Purple Shotgun", 0x8C49C7, false,
		Set.of(WeaponSkinType.GUN));
	public static final WeaponSkin SILENCER = builtIn("silencer_gun", "Silencer Gun", 0x505963, false,
		Set.of(WeaponSkinType.GUN));

	private final String id;
	private final String displayName;
	private final int color;
	private final boolean unlockedByDefault;
	private final Set<WeaponSkinType> types;

	private WeaponSkin(String id, String displayName, int color, boolean unlockedByDefault,
			Set<WeaponSkinType> types) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.unlockedByDefault = unlockedByDefault;
		this.types = Set.copyOf(types);
	}

	private static WeaponSkin builtIn(String id, String displayName, int color, boolean unlockedByDefault,
			Set<WeaponSkinType> types) {
		WeaponSkin skin = new WeaponSkin(id, displayName, color, unlockedByDefault, types);
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

	private static String normalize(String raw) {
		return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof WeaponSkin skin && id.equals(skin.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
