package dev.mapselect.leaderboard;

public final class LeaderboardLayout {
	public static final float TEXT_SCALE = 0.0125F;
	public static final float SCREEN_MARGIN = 0.075F;
	public static final int HEADER_HEIGHT = 15;
	public static final int FOOTER_HEIGHT = 11;
	public static final int ROW_HEIGHT = 10;

	private LeaderboardLayout() {
	}

	public static int pixelWidth(int blockWidth) {
		return Math.max(24,
			(int) Math.floor((Math.max(1, blockWidth) - SCREEN_MARGIN * 2.0F) / TEXT_SCALE));
	}

	public static int pixelHeight(int blockHeight) {
		return Math.max(24,
			(int) Math.floor((Math.max(1, blockHeight) - SCREEN_MARGIN * 2.0F) / TEXT_SCALE));
	}

	public static int visibleRows(int blockHeight) {
		return Math.max(1, (pixelHeight(blockHeight) - HEADER_HEIGHT - FOOTER_HEIGHT) / ROW_HEIGHT);
	}

	public static int maximumScrollOffset(int entryCount, int blockHeight) {
		return Math.max(0, Math.max(0, entryCount) - visibleRows(blockHeight));
	}

	public static int clampScrollOffset(int currentOffset, int amount, int entryCount, int blockHeight) {
		int maximum = maximumScrollOffset(entryCount, blockHeight);
		int current = Math.max(0, Math.min(maximum, currentOffset));
		long requested = (long) current + amount;
		return (int) Math.max(0L, Math.min(maximum, requested));
	}
}
