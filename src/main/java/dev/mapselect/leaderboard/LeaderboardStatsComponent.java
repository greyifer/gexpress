package dev.mapselect.leaderboard;

import dev.mapselect.MapSelect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LeaderboardStatsComponent implements org.ladysnake.cca.api.v3.component.Component {
	public static final ComponentKey<LeaderboardStatsComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "leaderboard_stats"),
		LeaderboardStatsComponent.class
	);
	private final Map<UUID, LeaderboardPlayerStats> statsByPlayer = new LinkedHashMap<>();

	public LeaderboardStatsComponent(World world) {
	}

	public void touch(UUID playerId, String name) {
		if (playerId == null) return;
		statsByPlayer.computeIfAbsent(playerId, ignored -> new LeaderboardPlayerStats()).touch(name);
	}

	public void remove(UUID playerId) {
		if (playerId != null) statsByPlayer.remove(playerId);
	}

	public void recordKill(UUID playerId, String name) {
		LeaderboardPlayerStats stats = stats(playerId, name);
		if (stats != null) stats.recordKill();
	}

	public void recordRound(UUID playerId, String name, boolean won, String side) {
		LeaderboardPlayerStats stats = stats(playerId, name);
		if (stats != null) stats.recordRound(won, side);
	}

	public void recordAliveTime(UUID playerId, String name, long ticks) {
		LeaderboardPlayerStats stats = stats(playerId, name);
		if (stats != null) stats.recordAliveTime(ticks);
	}

	public long set(UUID playerId, String name, LeaderboardStat stat, long value) {
		LeaderboardPlayerStats stats = stats(playerId, name);
		return stats == null || stat == null ? 0L : stats.set(stat, value);
	}

	public long add(UUID playerId, String name, LeaderboardStat stat, long amount) {
		LeaderboardPlayerStats stats = stats(playerId, name);
		return stats == null || stat == null ? 0L : stats.add(stat, amount);
	}

	public void reset(UUID playerId, String name) {
		LeaderboardPlayerStats stats = stats(playerId, name);
		if (stats != null) stats.reset();
	}

	public AdminSnapshot snapshot(UUID playerId, String name) {
		LeaderboardPlayerStats stats = stats(playerId, name);
		return stats == null ? AdminSnapshot.EMPTY : new AdminSnapshot(stats.name(), stats.kills(), stats.wins(),
			stats.killerWins(), stats.neutralWins(), stats.civilianWins(), stats.currentWinStreak(),
			stats.longestWinStreak(), stats.longestAliveTicks() / 20L);
	}

	public List<Entry> entries() {
		List<Entry> entries = new ArrayList<>(statsByPlayer.size());
		for (Map.Entry<UUID, LeaderboardPlayerStats> entry : statsByPlayer.entrySet()) {
			LeaderboardPlayerStats stats = entry.getValue();
			entries.add(new Entry(entry.getKey(), stats.name(), stats.kills(), stats.wins(),
				stats.killerWins(), stats.neutralWins(), stats.civilianWins(),
				stats.longestWinStreak(), stats.longestAliveTicks()));
		}
		return List.copyOf(entries);
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		statsByPlayer.clear();
		NbtList entries = tag.getList("players", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < entries.size(); i++) {
			NbtCompound entry = entries.getCompound(i);
			UUID playerId = parseUuid(entry.getString("uuid"));
			if (playerId == null) continue;
			LeaderboardPlayerStats stats = new LeaderboardPlayerStats(
				entry.getString("name"),
				entry.getInt("kills"),
				entry.getInt("wins"),
				entry.getInt("killerWins"),
				entry.getInt("neutralWins"),
				entry.getInt("civilianWins"),
				entry.getInt("currentWinStreak"),
				entry.getInt("longestWinStreak"),
				entry.getLong("longestAliveTicks"));
			statsByPlayer.put(playerId, stats);
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList entries = new NbtList();
		for (Map.Entry<UUID, LeaderboardPlayerStats> entry : statsByPlayer.entrySet()) {
			LeaderboardPlayerStats stats = entry.getValue();
			NbtCompound out = new NbtCompound();
			out.putString("uuid", entry.getKey().toString());
			out.putString("name", stats.name());
			out.putInt("kills", stats.kills());
			out.putInt("wins", stats.wins());
			out.putInt("killerWins", stats.killerWins());
			out.putInt("neutralWins", stats.neutralWins());
			out.putInt("civilianWins", stats.civilianWins());
			out.putInt("currentWinStreak", stats.currentWinStreak());
			out.putInt("longestWinStreak", stats.longestWinStreak());
			out.putLong("longestAliveTicks", stats.longestAliveTicks());
			entries.add(out);
		}
		tag.put("players", entries);
	}

	private LeaderboardPlayerStats stats(UUID playerId, String name) {
		if (playerId == null) return null;
		LeaderboardPlayerStats stats = statsByPlayer.computeIfAbsent(playerId,
			ignored -> new LeaderboardPlayerStats());
		stats.touch(name);
		return stats;
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) return null;
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public record Entry(UUID playerId, String name, int kills, int wins, int killerWins,
			int neutralWins, int civilianWins, int longestWinStreak, long longestAliveTicks) {
	}

	public record AdminSnapshot(String name, int kills, int wins, int killerWins, int neutralWins,
			int civilianWins, int currentWinStreak, int longestWinStreak, long longestAliveSeconds) {
		private static final AdminSnapshot EMPTY = new AdminSnapshot("", 0, 0, 0, 0, 0, 0, 0, 0L);
	}
}
