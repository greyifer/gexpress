package dev.mapselect.leaderboard;

final class LeaderboardPlayerStats {
	private static final int MAX_NAME_LENGTH = 64;
	private String name;
	private int kills;
	private int wins;
	private int killerWins;
	private int neutralWins;
	private int civilianWins;
	private int currentWinStreak;
	private int longestWinStreak;
	private long longestAliveTicks;

	LeaderboardPlayerStats() {
		this("", 0, 0, 0, 0, 0, 0, 0, 0L);
	}

	LeaderboardPlayerStats(String name, int kills, int wins, int killerWins, int neutralWins,
			int civilianWins, int currentWinStreak, int longestWinStreak, long longestAliveTicks) {
		this.name = sanitizeName(name);
		this.kills = nonNegative(kills);
		this.wins = nonNegative(wins);
		this.killerWins = nonNegative(killerWins);
		this.neutralWins = nonNegative(neutralWins);
		this.civilianWins = nonNegative(civilianWins);
		this.currentWinStreak = nonNegative(currentWinStreak);
		this.longestWinStreak = nonNegative(longestWinStreak);
		this.longestAliveTicks = Math.max(0L, longestAliveTicks);
	}

	void touch(String name) {
		String cleanName = sanitizeName(name);
		if (!cleanName.isBlank()) this.name = cleanName;
	}

	void recordKill() {
		kills = increment(kills);
	}

	void recordRound(boolean won, String side) {
		if (!won) {
			currentWinStreak = 0;
			return;
		}
		wins = increment(wins);
		currentWinStreak = increment(currentWinStreak);
		longestWinStreak = Math.max(longestWinStreak, currentWinStreak);
		if ("civilian".equals(side)) {
			civilianWins = increment(civilianWins);
		} else if ("neutral".equals(side)) {
			neutralWins = increment(neutralWins);
		} else if (side != null && !side.isBlank()) {
			killerWins = increment(killerWins);
		}
	}

	void recordAliveTime(long ticks) {
		longestAliveTicks = Math.max(longestAliveTicks, Math.max(0L, ticks));
	}

	long value(LeaderboardStat stat) {
		return switch (stat) {
			case KILLS -> kills;
			case WINS -> wins;
			case KILLER_WINS -> killerWins;
			case NEUTRAL_WINS -> neutralWins;
			case CIVILIAN_WINS -> civilianWins;
			case CURRENT_WIN_STREAK -> currentWinStreak;
			case LONGEST_WIN_STREAK -> longestWinStreak;
			case LONGEST_ALIVE_SECONDS -> longestAliveTicks / 20L;
		};
	}

	long set(LeaderboardStat stat, long value) {
		long clean = Math.max(0L, value);
		int intValue = (int) Math.min(Integer.MAX_VALUE, clean);
		switch (stat) {
			case KILLS -> kills = intValue;
			case WINS -> wins = intValue;
			case KILLER_WINS -> killerWins = intValue;
			case NEUTRAL_WINS -> neutralWins = intValue;
			case CIVILIAN_WINS -> civilianWins = intValue;
			case CURRENT_WIN_STREAK -> {
				currentWinStreak = intValue;
				longestWinStreak = Math.max(longestWinStreak, currentWinStreak);
			}
			case LONGEST_WIN_STREAK -> {
				longestWinStreak = intValue;
				currentWinStreak = Math.min(currentWinStreak, longestWinStreak);
			}
			case LONGEST_ALIVE_SECONDS -> longestAliveTicks = clean > Long.MAX_VALUE / 20L
				? Long.MAX_VALUE : clean * 20L;
		}
		return value(stat);
	}

	long add(LeaderboardStat stat, long amount) {
		long current = value(stat);
		long next;
		if (amount > 0L && current > Long.MAX_VALUE - amount) {
			next = Long.MAX_VALUE;
		} else if (amount < 0L && current < -amount) {
			next = 0L;
		} else {
			next = current + amount;
		}
		return set(stat, next);
	}

	void reset() {
		kills = 0;
		wins = 0;
		killerWins = 0;
		neutralWins = 0;
		civilianWins = 0;
		currentWinStreak = 0;
		longestWinStreak = 0;
		longestAliveTicks = 0L;
	}

	String name() {
		return name;
	}

	int kills() {
		return kills;
	}

	int wins() {
		return wins;
	}

	int killerWins() {
		return killerWins;
	}

	int neutralWins() {
		return neutralWins;
	}

	int civilianWins() {
		return civilianWins;
	}

	int currentWinStreak() {
		return currentWinStreak;
	}

	int longestWinStreak() {
		return longestWinStreak;
	}

	long longestAliveTicks() {
		return longestAliveTicks;
	}

	private static int increment(int value) {
		return value == Integer.MAX_VALUE ? value : Math.max(0, value) + 1;
	}

	private static int nonNegative(int value) {
		return Math.max(0, value);
	}

	private static String sanitizeName(String value) {
		if (value == null) return "";
		String clean = value.strip();
		return clean.length() <= MAX_NAME_LENGTH ? clean : clean.substring(0, MAX_NAME_LENGTH);
	}
}
