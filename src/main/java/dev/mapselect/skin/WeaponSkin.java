package dev.mapselect.skin;

import java.util.Locale;

public enum WeaponSkin {
	DEFAULT("default", "Default", 0x8EA1AD, true),
	HOST("host", "Host", 0x559CFF, false),
	TRUSTED("trusted", "Trusted", 0xF2C94C, false),
	DEV("dev", "Dev", 0xCBFF2E, false);

	private final String id;
	private final String displayName;
	private final int color;
	private final boolean unlockedByDefault;

	WeaponSkin(String id, String displayName, int color, boolean unlockedByDefault) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.unlockedByDefault = unlockedByDefault;
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

	public static WeaponSkin byId(String raw) {
		if (raw == null) return null;
		String cleaned = raw.trim().toLowerCase(Locale.ROOT);
		for (WeaponSkin skin : values()) {
			if (skin.id.equals(cleaned)) return skin;
		}
		return null;
	}
}
