package dev.mapselect.leaderboard;

public enum LeaderboardCategory {
	KILLS("Kills"),
	LEVEL("Level"),
	WINS("Wins"),
	KILLER_WINS("Killer Wins"),
	NEUTRAL_WINS("Neutral Wins"),
	CIVILIAN_WINS("Civilian Wins"),
	LONGEST_WIN_STREAK("Longest Win Streak"),
	LONGEST_ALIVE("Longest Alive");

	private static final LeaderboardCategory[] VALUES = values();
	private final String displayName;

	LeaderboardCategory(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	public LeaderboardCategory cycle(int amount) {
		return VALUES[Math.floorMod(ordinal() + amount, VALUES.length)];
	}

	public static LeaderboardCategory byOrdinal(int ordinal) {
		return VALUES[Math.floorMod(ordinal, VALUES.length)];
	}
}
