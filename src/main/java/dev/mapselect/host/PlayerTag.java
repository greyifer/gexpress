package dev.mapselect.host;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Locale;

public enum PlayerTag {
	OWNER("owner", "Owner", 0x196266, true),
	PASSENGER("passenger", "Passenger", 0x3C8AC9, true),
	HOST("host", "Host", 0x559CFF, true),
	TRUSTED("trusted", "Trusted", 0xF2C94C, true),
	STAFF("staff", "Staff", 0x79B9A9, true),
	DEV("dev", "Dev", 0xCBFF2E, false);

	private final String id;
	private final String displayName;
	private final int color;
	private final boolean assignable;

	PlayerTag(String id, String displayName, int color, boolean assignable) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.assignable = assignable;
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

	public boolean assignable() {
		return assignable;
	}

	public MutableText text() {
		return Text.literal(displayName).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
	}

	public static PlayerTag byId(String raw) {
		if (raw == null) return null;
		String cleaned = raw.trim().toLowerCase(Locale.ROOT);
		for (PlayerTag tag : values()) {
			if (tag.id.equals(cleaned)) return tag;
		}
		return null;
	}
}
