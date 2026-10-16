package dev.mapselect.block;

import net.minecraft.util.StringIdentifiable;

public enum LargeDoorPart implements StringIdentifiable {
	LOWER("lower", 0),
	MIDDLE("middle", 1),
	UPPER("upper", 2);

	private final String name;
	private final int offsetFromLower;

	LargeDoorPart(String name, int offsetFromLower) {
		this.name = name;
		this.offsetFromLower = offsetFromLower;
	}

	@Override
	public String asString() {
		return name;
	}

	public int offsetFromLower() {
		return offsetFromLower;
	}

	public LargeDoorPart above() {
		return switch (this) {
			case LOWER -> MIDDLE;
			case MIDDLE -> UPPER;
			case UPPER -> UPPER;
		};
	}

	public LargeDoorPart below() {
		return switch (this) {
			case LOWER -> LOWER;
			case MIDDLE -> LOWER;
			case UPPER -> MIDDLE;
		};
	}
}
