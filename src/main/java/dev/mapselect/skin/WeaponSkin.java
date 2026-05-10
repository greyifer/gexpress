package dev.mapselect.skin;

import java.util.Locale;
import java.util.Set;

public enum WeaponSkin {
	DEFAULT("default", "Default", 0x8EA1AD, true, Set.of(WeaponSkinType.KNIFE, WeaponSkinType.GUN)),
	HOST("host", "Host", 0x559CFF, false, Set.of(WeaponSkinType.KNIFE, WeaponSkinType.GUN)),
	TRUSTED("trusted", "Trusted", 0xF2C94C, false, Set.of(WeaponSkinType.KNIFE, WeaponSkinType.GUN)),
	DEV("dev", "Dev", 0xCBFF2E, false, Set.of(WeaponSkinType.KNIFE, WeaponSkinType.GUN)),
	PASSENGER("passenger", "Passenger", 0x8EA1AD, false, Set.of(WeaponSkinType.GUN)),
	COLA("cola", "Cola", 0xE63B34, false, Set.of(WeaponSkinType.GUN)),
	GOLD("gold", "Gold", 0xF2C94C, false, Set.of(WeaponSkinType.GUN)),
	JEM("jem", "Jem", 0x82D97A, false, Set.of(WeaponSkinType.GUN));

	private final String id;
	private final String displayName;
	private final int color;
	private final boolean unlockedByDefault;
	private final Set<WeaponSkinType> types;

	WeaponSkin(String id, String displayName, int color, boolean unlockedByDefault, Set<WeaponSkinType> types) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.unlockedByDefault = unlockedByDefault;
		this.types = types;
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

	public static WeaponSkin byId(String raw) {
		if (raw == null) return null;
		String cleaned = raw.trim().toLowerCase(Locale.ROOT);
		for (WeaponSkin skin : values()) {
			if (skin.id.equals(cleaned)) return skin;
		}
		return null;
	}
}
