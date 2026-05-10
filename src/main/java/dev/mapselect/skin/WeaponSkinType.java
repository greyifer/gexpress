package dev.mapselect.skin;

import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.item.Item;

import java.util.Locale;

public enum WeaponSkinType {
	KNIFE("knife", "Knife", WatheItems.KNIFE),
	GUN("gun", "Gun", WatheItems.REVOLVER);

	private final String id;
	private final String displayName;
	private final Item item;

	WeaponSkinType(String id, String displayName, Item item) {
		this.id = id;
		this.displayName = displayName;
		this.item = item;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public Item item() {
		return item;
	}

	public static WeaponSkinType byId(String raw) {
		if (raw == null) return null;
		String cleaned = raw.trim().toLowerCase(Locale.ROOT);
		if ("revolver".equals(cleaned) || "pistol".equals(cleaned)) cleaned = "gun";
		for (WeaponSkinType type : values()) {
			if (type.id.equals(cleaned)) return type;
		}
		return null;
	}
}
