package dev.mapselect.role.mafia;

public enum TakeoverSide {
	LIME(0, 0xA8F05A, "announcement.win.gexpress.takeover_lime"),
	PURPLE(1, 0xB76CFF, "announcement.win.gexpress.takeover_purple");

	private final int id;
	private final int color;
	private final String winTranslationKey;

	TakeoverSide(int id, int color, String winTranslationKey) {
		this.id = id;
		this.color = color;
		this.winTranslationKey = winTranslationKey;
	}

	public int id() {
		return id;
	}

	public int color() {
		return color;
	}

	public String winTranslationKey() {
		return winTranslationKey;
	}
}
