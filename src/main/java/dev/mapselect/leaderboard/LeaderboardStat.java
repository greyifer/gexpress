package dev.mapselect.leaderboard;

import java.util.Arrays;
import java.util.List;

/** Player statistics that staff may inspect and edit through the leaderboard command. */
public enum LeaderboardStat {
	KILLS("kills"),
	WINS("wins"),
	KILLER_WINS("killer_wins"),
	NEUTRAL_WINS("neutral_wins"),
	CIVILIAN_WINS("civilian_wins"),
	CURRENT_WIN_STREAK("current_win_streak"),
	LONGEST_WIN_STREAK("longest_win_streak"),
	LONGEST_ALIVE_SECONDS("longest_alive_seconds");

	private final String id;

	LeaderboardStat(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static LeaderboardStat byId(String id) {
		if (id == null) return null;
		for (LeaderboardStat stat : values()) {
			if (stat.id.equalsIgnoreCase(id)) return stat;
		}
		return null;
	}

	public static List<String> ids() {
		return Arrays.stream(values()).map(LeaderboardStat::id).toList();
	}
}
