package dev.mapselect.client.leaderboard;

import dev.mapselect.leaderboard.LeaderboardCategory;
import dev.mapselect.network.leaderboard.LeaderboardDisplayPayload;
import dev.mapselect.network.leaderboard.LeaderboardSnapshotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ClientLeaderboardState {
	private static List<LeaderboardSnapshotPayload.Entry> entries = List.of();
	private static final Map<DisplayKey, DisplayState> displays = new HashMap<>();

	private ClientLeaderboardState() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LeaderboardSnapshotPayload.ID, (payload, context) ->
			context.client().execute(() -> entries = payload.entries()));
		ClientPlayNetworking.registerGlobalReceiver(LeaderboardDisplayPayload.ID, (payload, context) ->
			context.client().execute(() -> {
				if (context.client().world == null) return;
				displays.put(new DisplayKey(context.client().world.getRegistryKey(), payload.origin()),
					new DisplayState(payload.category(), payload.scrollOffset()));
			}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			entries = List.of();
			displays.clear();
		});
	}

	public static DisplayState display(World world, BlockPos origin,
			LeaderboardCategory defaultCategory, int defaultScrollOffset) {
		LeaderboardCategory category = defaultCategory == null ? LeaderboardCategory.KILLS : defaultCategory;
		DisplayState fallback = new DisplayState(category, Math.max(0, defaultScrollOffset));
		if (world == null || origin == null) return fallback;
		return displays.getOrDefault(new DisplayKey(world.getRegistryKey(), origin), fallback);
	}

	public static List<LeaderboardSnapshotPayload.Entry> ranked(LeaderboardCategory category) {
		if (category == null) category = LeaderboardCategory.KILLS;
		LeaderboardCategory selected = category;
		return entries.stream()
			.filter(entry -> selected == LeaderboardCategory.LEVEL || value(entry, selected) > 0L)
			.sorted(Comparator
				.comparingLong((LeaderboardSnapshotPayload.Entry entry) -> value(entry, selected)).reversed()
				.thenComparing(LeaderboardSnapshotPayload.Entry::name, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(LeaderboardSnapshotPayload.Entry::playerId))
			.toList();
	}

	public static long value(LeaderboardSnapshotPayload.Entry entry, LeaderboardCategory category) {
		if (entry == null || category == null) return 0L;
		return switch (category) {
			case KILLS -> entry.kills();
			case LEVEL -> entry.level();
			case WINS -> entry.wins();
			case KILLER_WINS -> entry.killerWins();
			case NEUTRAL_WINS -> entry.neutralWins();
			case CIVILIAN_WINS -> entry.civilianWins();
			case LONGEST_WIN_STREAK -> entry.longestWinStreak();
			case LONGEST_ALIVE -> entry.longestAliveTicks();
		};
	}

	public record DisplayState(LeaderboardCategory category, int scrollOffset) {
	}

	private record DisplayKey(RegistryKey<World> world, BlockPos origin) {
		private DisplayKey {
			origin = origin.toImmutable();
		}
	}
}
