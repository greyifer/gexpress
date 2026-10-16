package dev.mapselect.leaderboard;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileResult;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.block.LeaderboardBlockEntity;
import dev.mapselect.block.LeaderboardPanel;
import dev.mapselect.game.RoundParticipantTracker;
import dev.mapselect.level.LevelComponent;
import dev.mapselect.network.leaderboard.LeaderboardDisplayPayload;
import dev.mapselect.network.leaderboard.LeaderboardScrollPayload;
import dev.mapselect.network.leaderboard.LeaderboardSnapshotPayload;
import dev.mapselect.role.RoleTeams;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LeaderboardManager {
	private static final int SNAPSHOT_INTERVAL_TICKS = 20;
	private static final int KILL_DEDUP_TICKS = 20;
	private static final int MAX_CONCURRENT_SKIN_LOOKUPS = 4;
	private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0D;
	private static final Map<RegistryKey<World>, RoundSession> SESSIONS = new HashMap<>();
	private static final Map<UUID, RecentKill> RECENT_KILLS = new HashMap<>();
	private static final Map<UUID, SkinProperty> SKIN_PROPERTIES = new HashMap<>();
	private static final Set<UUID> REQUESTED_SKINS = new LinkedHashSet<>();
	private static final Set<UUID> VERIFIED_PLAYERS = new LinkedHashSet<>();
	private static final Set<UUID> AUTOMATED_PLAYERS = new LinkedHashSet<>();
	private static final Map<PlayerDisplayKey, PlayerDisplayState> DISPLAY_STATES = new HashMap<>();
	private static LeaderboardSnapshotPayload lastBroadcast;
	private static int ticksUntilSnapshot;
	private static int activeSkinLookups;

	private LeaderboardManager() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(LeaderboardSnapshotPayload.ID, LeaderboardSnapshotPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LeaderboardDisplayPayload.ID, LeaderboardDisplayPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(LeaderboardScrollPayload.ID, LeaderboardScrollPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LeaderboardScrollPayload.ID, (payload, context) ->
			context.server().execute(() -> handleScroll(context.player(), payload)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> {
				touch(handler.player);
				sendSnapshot(handler.player, createSnapshot(server));
			}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			server.execute(() -> DISPLAY_STATES.keySet().removeIf(
				key -> key.playerId().equals(handler.player.getUuid()))));
		GameEvents.ON_FINISH_INITIALIZE.register(LeaderboardManager::beginRound);
		GameEvents.ON_FINISH_FINALIZE.register(LeaderboardManager::finishRound);
		AllowPlayerDeath.EVENT.register(LeaderboardManager::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(LeaderboardManager::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(LeaderboardManager::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	private static void beginRound(World rawWorld, GameWorldComponent game) {
		if (!(rawWorld instanceof ServerWorld world) || game == null) return;
		Set<UUID> participants = new LinkedHashSet<>();
		Map<UUID, String> sides = new LinkedHashMap<>();
		LeaderboardStatsComponent stats = component(world.getServer());
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (game.getRoles().containsKey(player.getUuid()) && !isAutomatedPlayer(player)) {
				participants.add(player.getUuid());
				String side = RoleTeams.sideKey(game, player);
				if (side != null) sides.put(player.getUuid(), side);
				stats.touch(player.getUuid(), player.getGameProfile().getName());
			}
		}
		SESSIONS.put(world.getRegistryKey(), new RoundSession(world.getTime(), participants, sides));
	}

	private static void finishRound(World rawWorld, GameWorldComponent game) {
		if (!(rawWorld instanceof ServerWorld world) || game == null) return;
		RoundSession session = SESSIONS.remove(world.getRegistryKey());
		GameRoundEndComponent roundEnd = GameRoundEndComponent.KEY.getNullable(world);
		if (roundEnd == null || roundEnd.getWinStatus() == GameFunctions.WinStatus.NONE) return;
		LeaderboardStatsComponent stats = component(world.getServer());
		Set<UUID> handled = new LinkedHashSet<>();
		for (GameRoundEndComponent.RoundEndData entry : roundEnd.getPlayers()) {
			if (entry == null || entry.player() == null) continue;
			UUID playerId = entry.player().getId();
			if (playerId == null || !handled.add(playerId)
					|| session != null && !session.includes(playerId)
					|| !RoundParticipantTracker.isRoundParticipant(world, game, playerId)
					|| RoundParticipantTracker.isRoundSpectator(world, game, playerId)) {
				continue;
			}
			String name = entry.player().getName();
			boolean won = roundEnd.didWin(playerId);
			String side = session == null ? RoleTeams.sideKey(game, playerId) : session.side(playerId);
			stats.recordRound(playerId, name, won, side);
			long aliveTicks = session == null ? 0L : session.aliveTicks(playerId, world.getTime());
			stats.recordAliveTime(playerId, name, aliveTicks);
		}
		ticksUntilSnapshot = 0;
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		recordDeath(victim, killer);
		return true;
	}

	private static void afterDeath(net.minecraft.entity.LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayerEntity victim)) return;
		PlayerEntity killer = source != null && source.getAttacker() instanceof PlayerEntity player ? player : null;
		recordDeath(victim, killer);
	}

	private static void recordDeath(PlayerEntity victim, PlayerEntity killer) {
		if (!(victim instanceof ServerPlayerEntity serverVictim)
				|| !(serverVictim.getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (isAutomatedPlayer(serverVictim)
				|| killer instanceof ServerPlayerEntity serverKiller && isAutomatedPlayer(serverKiller)) {
			return;
		}
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || !game.isRunning()
				|| !RoundParticipantTracker.isRoundParticipant(world, game, serverVictim.getUuid())) {
			return;
		}
		RoundSession session = SESSIONS.get(world.getRegistryKey());
		if (session != null) session.markDead(serverVictim.getUuid(), world.getTime());
		if (!(killer instanceof ServerPlayerEntity serverKiller) || serverKiller == serverVictim
				|| serverKiller.getWorld() != world || !GameFunctions.isPlayerAliveAndSurvival(serverKiller)
				|| !game.getRoles().containsKey(serverKiller.getUuid())
				|| !RoundParticipantTracker.isRoundParticipant(world, game, serverKiller.getUuid())
				|| wasRecentlyCounted(serverVictim.getUuid(), serverKiller.getUuid(),
					world.getRegistryKey(), world.getTime())) {
			return;
		}
		component(world.getServer()).recordKill(serverKiller.getUuid(), serverKiller.getGameProfile().getName());
		ticksUntilSnapshot = 0;
	}

	private static boolean wasRecentlyCounted(UUID victimId, UUID killerId, RegistryKey<World> world, long tick) {
		RECENT_KILLS.entrySet().removeIf(entry ->
			entry.getValue().world().equals(world) && tick - entry.getValue().tick() > KILL_DEDUP_TICKS);
		RecentKill previous = RECENT_KILLS.get(victimId);
		if (previous != null && previous.killerId().equals(killerId) && previous.world().equals(world)
				&& tick - previous.tick() <= KILL_DEDUP_TICKS) {
			return true;
		}
		RECENT_KILLS.put(victimId, new RecentKill(killerId, world, tick));
		return false;
	}

	private static void handleScroll(ServerPlayerEntity player, LeaderboardScrollPayload payload) {
		if (player == null || payload == null || payload.amount() == 0
				|| !(player.getWorld() instanceof ServerWorld world)
				|| player.getPos().squaredDistanceTo(Vec3d.ofCenter(payload.pos()))
					> MAX_INTERACTION_DISTANCE_SQUARED) {
			return;
		}
		LeaderboardPanel panel = LeaderboardPanel.find(world, payload.pos());
		if (panel == null) return;
		PlayerDisplayKey key = displayKey(player, panel);
		PlayerDisplayState current = displayState(player, panel);
		int entryCount = rankedEntryCount(current.category());
		int nextOffset = LeaderboardLayout.clampScrollOffset(current.scrollOffset(), payload.amount(),
			entryCount, panel.height());
		PlayerDisplayState next = new PlayerDisplayState(current.category(), nextOffset);
		DISPLAY_STATES.put(key, next);
		sendDisplay(player, panel, next);
	}

	public static void changeCategory(ServerPlayerEntity player, LeaderboardPanel panel, int amount) {
		if (player == null || panel == null || amount == 0) return;
		PlayerDisplayState current = displayState(player, panel);
		PlayerDisplayState next = new PlayerDisplayState(current.category().cycle(amount), 0);
		DISPLAY_STATES.put(displayKey(player, panel), next);
		sendDisplay(player, panel, next);
	}

	private static PlayerDisplayState displayState(ServerPlayerEntity player, LeaderboardPanel panel) {
		PlayerDisplayKey key = displayKey(player, panel);
		PlayerDisplayState existing = DISPLAY_STATES.get(key);
		if (existing != null) return existing;
		LeaderboardBlockEntity controller = panel.controller(player.getWorld());
		LeaderboardCategory category = controller == null ? LeaderboardCategory.KILLS : controller.category();
		int requestedOffset = controller == null ? 0 : controller.scrollOffset();
		int offset = Math.max(0, Math.min(maximumScrollOffset(panel, category), requestedOffset));
		PlayerDisplayState created = new PlayerDisplayState(category, offset);
		DISPLAY_STATES.put(key, created);
		return created;
	}

	private static PlayerDisplayKey displayKey(ServerPlayerEntity player, LeaderboardPanel panel) {
		return new PlayerDisplayKey(player.getUuid(), player.getWorld().getRegistryKey(), panel.origin());
	}

	private static int maximumScrollOffset(LeaderboardPanel panel, LeaderboardCategory category) {
		return LeaderboardLayout.maximumScrollOffset(rankedEntryCount(category), panel.height());
	}

	private static int rankedEntryCount(LeaderboardCategory category) {
		int entryCount = 0;
		if (lastBroadcast != null) {
			for (LeaderboardSnapshotPayload.Entry entry : lastBroadcast.entries()) {
				if (category == LeaderboardCategory.LEVEL || leaderboardValue(entry, category) > 0L) {
					entryCount++;
				}
			}
		}
		return entryCount;
	}

	private static long leaderboardValue(LeaderboardSnapshotPayload.Entry entry,
			LeaderboardCategory category) {
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

	private static void tick(MinecraftServer server) {
		if (server == null || ticksUntilSnapshot-- > 0) return;
		ticksUntilSnapshot = SNAPSHOT_INTERVAL_TICKS - 1;
		LeaderboardSnapshotPayload snapshot = createSnapshot(server);
		if (snapshot.equals(lastBroadcast)) return;
		lastBroadcast = snapshot;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			sendSnapshot(player, snapshot);
		}
	}

	private static LeaderboardSnapshotPayload createSnapshot(MinecraftServer server) {
		LeaderboardStatsComponent component = component(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (isAutomatedPlayer(player)) {
				AUTOMATED_PLAYERS.add(player.getUuid());
				component.remove(player.getUuid());
				continue;
			}
			AUTOMATED_PLAYERS.remove(player.getUuid());
			VERIFIED_PLAYERS.add(player.getUuid());
			component.touch(player.getUuid(), player.getGameProfile().getName());
			cacheSkin(player.getGameProfile());
		}
		Map<UUID, Integer> levels = new LinkedHashMap<>();
		for (ServerWorld world : server.getWorlds()) {
			LevelComponent levelComponent = LevelComponent.KEY.getNullable(world);
			if (levelComponent == null) continue;
			for (Map.Entry<UUID, Integer> entry : levelComponent.entries().entrySet()) {
				int level = LevelComponent.levelForXp(entry.getValue());
				levels.merge(entry.getKey(), level, Math::max);
			}
		}
		Map<UUID, LeaderboardStatsComponent.Entry> statsByPlayer = new LinkedHashMap<>();
		for (LeaderboardStatsComponent.Entry entry : component.entries()) {
			statsByPlayer.put(entry.playerId(), entry);
		}
		Set<UUID> playerIds = new LinkedHashSet<>(statsByPlayer.keySet());
		playerIds.addAll(levels.keySet());
		java.util.List<LeaderboardSnapshotPayload.Entry> entries = new java.util.ArrayList<>(playerIds.size());
		for (UUID playerId : playerIds) {
			if (AUTOMATED_PLAYERS.contains(playerId)) continue;
			LeaderboardStatsComponent.Entry stats = statsByPlayer.get(playerId);
			String name = stats == null || stats.name().isBlank()
				? resolveName(server, playerId)
				: stats.name();
			if (!isPossibleRealPlayerName(name)
					|| name.equalsIgnoreCase(playerId.toString().substring(0, 8))) {
				continue;
			}
			SkinProperty skin = SKIN_PROPERTIES.get(playerId);
			if (!VERIFIED_PLAYERS.contains(playerId)) {
				requestSkin(server, playerId, name);
				continue;
			}
			entries.add(new LeaderboardSnapshotPayload.Entry(playerId, name,
				skin == null ? "" : skin.value(),
				skin == null ? "" : skin.signature(),
				stats == null ? 0 : stats.kills(),
				levels.getOrDefault(playerId, 1),
				stats == null ? 0 : stats.wins(),
				stats == null ? 0 : stats.killerWins(),
				stats == null ? 0 : stats.neutralWins(),
				stats == null ? 0 : stats.civilianWins(),
				stats == null ? 0 : stats.longestWinStreak(),
				stats == null ? 0L : stats.longestAliveTicks()));
		}
		entries.sort(java.util.Comparator.comparing(LeaderboardSnapshotPayload.Entry::name,
			String.CASE_INSENSITIVE_ORDER).thenComparing(LeaderboardSnapshotPayload.Entry::playerId));
		return new LeaderboardSnapshotPayload(entries);
	}

	private static void touch(ServerPlayerEntity player) {
		if (player != null && player.getServer() != null) {
			if (isAutomatedPlayer(player)) {
				AUTOMATED_PLAYERS.add(player.getUuid());
				component(player.getServer()).remove(player.getUuid());
				ticksUntilSnapshot = 0;
				return;
			}
			AUTOMATED_PLAYERS.remove(player.getUuid());
			VERIFIED_PLAYERS.add(player.getUuid());
			component(player.getServer()).touch(player.getUuid(), player.getGameProfile().getName());
			ticksUntilSnapshot = 0;
		}
	}

	private static String resolveName(MinecraftServer server, UUID playerId) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(playerId);
		if (online != null) return online.getGameProfile().getName();
		GameProfile cached = server.getUserCache() == null
			? null
			: server.getUserCache().getByUuid(playerId).orElse(null);
		return cached == null || cached.getName() == null || cached.getName().isBlank()
			? playerId.toString().substring(0, 8)
			: cached.getName();
	}

	private static void cacheSkin(GameProfile profile) {
		SkinProperty skin = skinProperty(profile);
		if (profile != null && profile.getId() != null) {
			VERIFIED_PLAYERS.add(profile.getId());
			REQUESTED_SKINS.add(profile.getId());
			if (skin != null) SKIN_PROPERTIES.put(profile.getId(), skin);
		}
	}

	private static void requestSkin(MinecraftServer server, UUID playerId, String playerName) {
		if (server == null || playerId == null || activeSkinLookups >= MAX_CONCURRENT_SKIN_LOOKUPS
				|| !REQUESTED_SKINS.add(playerId)) {
			return;
		}
		activeSkinLookups++;
		CompletableFuture.supplyAsync(() -> {
			try {
				SkinLookup lookup = fetchSkin(server, playerId);
				if (lookup.verified() || playerName == null || playerName.isBlank()) return lookup;
				GameProfile[] resolved = new GameProfile[1];
				server.getGameProfileRepo().findProfilesByNames(new String[]{playerName},
					new ProfileLookupCallback() {
						@Override
						public void onProfileLookupSucceeded(GameProfile profile) {
							resolved[0] = profile;
						}

						@Override
						public void onProfileLookupFailed(String profileName, Exception exception) {
						}
					});
				return resolved[0] == null ? SkinLookup.UNRESOLVED : fetchSkin(server, resolved[0].getId());
			} catch (Throwable ignored) {
				return SkinLookup.UNRESOLVED;
			}
		}).whenComplete((lookup, error) -> server.execute(() -> {
			activeSkinLookups = Math.max(0, activeSkinLookups - 1);
			if (lookup == null || !lookup.verified()) return;
			VERIFIED_PLAYERS.add(playerId);
			if (lookup.skin() != null) SKIN_PROPERTIES.put(playerId, lookup.skin());
			ticksUntilSnapshot = 0;
		}));
	}

	private static SkinLookup fetchSkin(MinecraftServer server, UUID playerId) {
		ProfileResult result = server.getSessionService().fetchProfile(playerId, true);
		return result == null
			? SkinLookup.UNRESOLVED
			: new SkinLookup(true, skinProperty(result.profile()));
	}

	private static SkinProperty skinProperty(GameProfile profile) {
		if (profile == null) return null;
		for (Property property : profile.getProperties().get("textures")) {
			if (property == null || property.value() == null || property.value().isBlank()) continue;
			return new SkinProperty(property.value(), property.signature() == null ? "" : property.signature());
		}
		return null;
	}

	private static boolean isPossibleRealPlayerName(String name) {
		if (name == null || name.length() < 3 || name.length() > 16) return false;
		for (int i = 0; i < name.length(); i++) {
			char character = name.charAt(i);
			if (!(character >= 'a' && character <= 'z')
					&& !(character >= 'A' && character <= 'Z')
					&& !(character >= '0' && character <= '9') && character != '_') {
				return false;
			}
		}
		return true;
	}

	private static boolean isAutomatedPlayer(ServerPlayerEntity player) {
		return player != null && (isCarpetFakeType(player.getClass())
			|| isCarpetFakeType(player.networkHandler == null ? null : player.networkHandler.getClass()));
	}

	private static boolean isCarpetFakeType(Class<?> type) {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			String name = current.getName().toLowerCase(java.util.Locale.ROOT);
			if (name.startsWith("carpet.") && name.contains("fake")) return true;
		}
		return false;
	}

	private static void sendSnapshot(ServerPlayerEntity player, LeaderboardSnapshotPayload snapshot) {
		if (player != null && snapshot != null
				&& ServerPlayNetworking.canSend(player, LeaderboardSnapshotPayload.ID)) {
			ServerPlayNetworking.send(player, snapshot);
		}
	}

	private static void sendDisplay(ServerPlayerEntity player, LeaderboardPanel panel,
			PlayerDisplayState display) {
		if (player != null && panel != null && display != null
				&& ServerPlayNetworking.canSend(player, LeaderboardDisplayPayload.ID)) {
			ServerPlayNetworking.send(player, new LeaderboardDisplayPayload(
				panel.origin(), display.category(), display.scrollOffset()));
		}
	}

	private static LeaderboardStatsComponent component(MinecraftServer server) {
		return LeaderboardStatsComponent.KEY.get(server.getOverworld());
	}

	public static LeaderboardStatsComponent.AdminSnapshot adminSnapshot(MinecraftServer server,
			GameProfile profile) {
		if (server == null || profile == null || profile.getId() == null) {
			return new LeaderboardStatsComponent.AdminSnapshot("", 0, 0, 0, 0, 0, 0, 0, 0L);
		}
		LeaderboardStatsComponent.AdminSnapshot snapshot =
			component(server).snapshot(profile.getId(), profile.getName());
		ticksUntilSnapshot = 0;
		return snapshot;
	}

	public static long adminSet(MinecraftServer server, GameProfile profile, LeaderboardStat stat,
			long value) {
		if (server == null || profile == null || profile.getId() == null || stat == null) return 0L;
		long result = component(server).set(profile.getId(), profile.getName(), stat, value);
		ticksUntilSnapshot = 0;
		return result;
	}

	public static long adminAdd(MinecraftServer server, GameProfile profile, LeaderboardStat stat,
			long amount) {
		if (server == null || profile == null || profile.getId() == null || stat == null) return 0L;
		long result = component(server).add(profile.getId(), profile.getName(), stat, amount);
		ticksUntilSnapshot = 0;
		return result;
	}

	public static void adminReset(MinecraftServer server, GameProfile profile) {
		if (server == null || profile == null || profile.getId() == null) return;
		component(server).reset(profile.getId(), profile.getName());
		ticksUntilSnapshot = 0;
	}

	private static void clear() {
		SESSIONS.clear();
		RECENT_KILLS.clear();
		SKIN_PROPERTIES.clear();
		REQUESTED_SKINS.clear();
		VERIFIED_PLAYERS.clear();
		AUTOMATED_PLAYERS.clear();
		DISPLAY_STATES.clear();
		lastBroadcast = null;
		ticksUntilSnapshot = 0;
		activeSkinLookups = 0;
	}

	private static final class RoundSession {
		private final long startTick;
		private final Set<UUID> participants;
		private final Map<UUID, String> sides;
		private final Map<UUID, Long> deathTicks = new HashMap<>();

		private RoundSession(long startTick, Set<UUID> participants, Map<UUID, String> sides) {
			this.startTick = startTick;
			this.participants = Set.copyOf(participants);
			this.sides = Map.copyOf(sides);
		}

		private boolean includes(UUID playerId) {
			return participants.contains(playerId);
		}

		private String side(UUID playerId) {
			return sides.get(playerId);
		}

		private void markDead(UUID playerId, long tick) {
			if (participants.contains(playerId)) deathTicks.putIfAbsent(playerId, tick);
		}

		private long aliveTicks(UUID playerId, long roundEndTick) {
			if (!participants.contains(playerId)) return 0L;
			return Math.max(0L, deathTicks.getOrDefault(playerId, roundEndTick) - startTick);
		}
	}

	private record RecentKill(UUID killerId, RegistryKey<World> world, long tick) {
	}

	private record SkinProperty(String value, String signature) {
	}

	private record SkinLookup(boolean verified, SkinProperty skin) {
		private static final SkinLookup UNRESOLVED = new SkinLookup(false, null);
	}

	private record PlayerDisplayKey(UUID playerId, RegistryKey<World> world, net.minecraft.util.math.BlockPos origin) {
		private PlayerDisplayKey {
			origin = origin.toImmutable();
		}
	}

	private record PlayerDisplayState(LeaderboardCategory category, int scrollOffset) {
	}
}
