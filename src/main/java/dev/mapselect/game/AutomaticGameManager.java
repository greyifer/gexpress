package dev.mapselect.game;

import dev.doctor4t.wathe.api.WatheMapEffects;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.command.GexpressFeedback;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.game.AutoGameStatusPayload;
import dev.mapselect.server.ServerAccessManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.agmas.harpymodloader.Harpymodloader;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class AutomaticGameManager {
	public static final int MINIMUM_READY_PLAYERS = 6;
	public static final int START_COUNTDOWN_SECONDS = 30;
	private static final int START_COUNTDOWN_TICKS = START_COUNTDOWN_SECONDS * 20;
	private static final int CLOSE_DELAY_AFTER_ROUND_TICKS = 20 * 60;
	private static final DateTimeFormatter FEEDBACK_TIME =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());
	private static ActiveAutoGame active;

	private AutomaticGameManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(AutoGameStatusPayload.ID, AutoGameStatusPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(AutomaticGameManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> syncJoiningPlayer(handler.player)));
	}

	public static boolean isActive() {
		return active != null;
	}

	public static int start(ServerCommandSource source, Instant start, Instant end) {
		if (source == null || start == null || end == null) return 0;
		if (!end.isAfter(start)) {
			source.sendError(Text.literal("The automatic game end time must be after the start time."));
			return 0;
		}
		if (active != null) {
			source.sendError(Text.literal("An automatic game is already active. Use /g game auto end first."));
			return 0;
		}
		if (!DiscordAutoGameBridge.isConfigured()) {
			source.sendError(Text.literal("The Discord bot endpoint is not configured for automatic game announcements."));
			return 0;
		}

		ServerWorld world = source.getWorld();
		ServerPlayerEntity creator = source.getEntity() instanceof ServerPlayerEntity player ? player : null;
		active = new ActiveAutoGame(start, end, creator == null ? null : creator.getUuid(), world.getRegistryKey());
		GexpressFeedback.send(source, Text.literal("Automatic game announced from ")
			.formatted(Formatting.GREEN)
			.append(Text.literal(FEEDBACK_TIME.format(start)).formatted(Formatting.WHITE))
			.append(Text.literal(" to ").formatted(Formatting.GRAY))
			.append(Text.literal(FEEDBACK_TIME.format(end)).formatted(Formatting.WHITE))
			.append(Text.literal(". The server will open for repeated automatic games during this window.").formatted(Formatting.GRAY)));

		DiscordAutoGameBridge.announce(source.getServer(), creator, start, end)
			.whenComplete((result, throwable) -> source.getServer().execute(() -> {
				if (throwable != null) {
					GexpressFeedback.send(source, Text.literal("Discord automatic game announcement failed, but the auto-game is still active.")
						.formatted(Formatting.RED));
					return;
				}
				if (!result.ok()) {
					GexpressFeedback.send(source, Text.literal("Discord automatic game announcement failed: HTTP " + result.statusCode()
							+ ". The auto-game is still active.")
						.formatted(Formatting.RED));
				}
			}));
		return 1;
	}

	public static int end(ServerCommandSource source) {
		ActiveAutoGame current = active;
		if (current == null) {
			source.sendError(Text.literal("There is no active automatic game."));
			return 0;
		}
		active = null;
		if (current.opened) {
			closeServer(source.getServer(), "Automatic game window ended early.");
		}
		syncInactive(source.getServer());
		GexpressFeedback.send(source, Text.literal("Automatic game ended.").formatted(Formatting.YELLOW));
		return 1;
	}

	private static void tick(MinecraftServer server) {
		ActiveAutoGame current = active;
		if (current == null) return;
		Instant now = Instant.now();
		ServerWorld world = server.getWorld(current.worldKey());
		if (world == null) {
			active = null;
			syncInactive(server);
			return;
		}

		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		int readyPlayers = GameFunctions.getReadyPlayerCount(world);
		boolean started = !now.isBefore(current.start());
		boolean ended = !now.isBefore(current.end());

		if (started && !current.opened) {
			current.opened = true;
			openServer(server);
		}

		if (!started) {
			syncStatus(server, current, world, -1, readyPlayers);
			return;
		}

		if (ended) {
			handleWindowEnd(server, current, world, game, readyPlayers);
			return;
		}

		if (isRoundBusy(game)) {
			current.resetCountdown();
			syncStatus(server, current, world, -1, readyPlayers);
			return;
		}

		if (readyPlayers < MINIMUM_READY_PLAYERS) {
			current.resetCountdown();
			syncStatus(server, current, world, -1, readyPlayers);
			return;
		}

		if (current.countdownTicks < 0) {
			current.countdownTicks = START_COUNTDOWN_TICKS;
			broadcast(server, Text.literal("Automatic game found " + readyPlayers + " ready players. Starting in "
					+ START_COUNTDOWN_SECONDS + "s.")
				.formatted(Formatting.GREEN));
		} else {
			current.countdownTicks--;
		}

		if (current.countdownTicks <= 0) {
			startAutomaticRound(world);
			current.resetCountdown();
			syncStatus(server, current, world, -1, readyPlayers);
			return;
		}

		syncStatus(server, current, world, countdownSeconds(current.countdownTicks), readyPlayers);
	}

	private static boolean isRoundBusy(GameWorldComponent game) {
		return game != null && game.getGameStatus() != GameWorldComponent.GameStatus.INACTIVE;
	}

	private static void handleWindowEnd(MinecraftServer server, ActiveAutoGame current, ServerWorld world,
			GameWorldComponent game, int readyPlayers) {
		current.resetCountdown();
		if (isRoundBusy(game)) {
			current.waitingForRoundEnd = true;
			current.inactiveTicksAfterEnd = 0;
			syncStatus(server, current, world, -1, readyPlayers);
			return;
		}

		if (current.waitingForRoundEnd) {
			current.inactiveTicksAfterEnd++;
			if (current.inactiveTicksAfterEnd < CLOSE_DELAY_AFTER_ROUND_TICKS) {
				syncStatus(server, current, world, -1, readyPlayers);
				return;
			}
		}

		active = null;
		closeServer(server, "Automatic game window closed.");
		syncInactive(server);
	}

	private static void startAutomaticRound(ServerWorld world) {
		GameWorldComponent game = GameWorldComponent.KEY.get(world);
		int normalDurationTicks = GameConstants.getInTicks(Harpymodloader.MODDED_GAMEMODE.defaultStartTime, 0);
		game.setGameMode(Harpymodloader.MODDED_GAMEMODE);
		game.setMapEffect(WatheMapEffects.HARPY_EXPRESS_NIGHT);
		GameTimeComponent.KEY.get(world).setResetTime(normalDurationTicks);
		game.setGameStatus(GameWorldComponent.GameStatus.STARTING);
	}

	private static void openServer(MinecraftServer server) {
		ServerAccessManager.setOpen(server, true);
		broadcast(server, Text.literal("Automatic game window opened.").formatted(Formatting.GREEN));
	}

	private static void closeServer(MinecraftServer server, String message) {
		ServerAccessManager.setOpen(server, false);
		int kicked = ServerAccessManager.kickBelowHost(server);
		Text closeMessage = Text.literal(message).formatted(Formatting.YELLOW);
		if (kicked > 0) {
			closeMessage = closeMessage.copy()
				.append(Text.literal(" Kicked " + kicked + " player" + (kicked == 1 ? "" : "s") + ".")
					.formatted(Formatting.GRAY));
		}
		broadcast(server, closeMessage);
	}

	private static void broadcast(MinecraftServer server, Text message) {
		if (server == null || message == null) return;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			player.sendMessage(message, false);
		}
	}

	private static int countdownSeconds(int countdownTicks) {
		return Math.max(0, (countdownTicks + 19) / 20);
	}

	private static void syncStatus(MinecraftServer server, ActiveAutoGame current, ServerWorld world,
			int countdownSeconds, int readyPlayers) {
		if (server == null || current == null || world == null) return;
		if (current.lastSyncedCountdownSeconds == countdownSeconds && current.lastSyncedReadyPlayers == readyPlayers) {
			return;
		}
		current.lastSyncedCountdownSeconds = countdownSeconds;
		current.lastSyncedReadyPlayers = readyPlayers;
		AutoGameStatusPayload payload =
			new AutoGameStatusPayload(true, countdownSeconds, readyPlayers, MINIMUM_READY_PLAYERS);
		for (ServerPlayerEntity player : world.getPlayers()) {
			PayloadSync.sendIfChanged(player, AutoGameStatusPayload.ID, payload);
		}
	}

	private static void syncJoiningPlayer(ServerPlayerEntity player) {
		ActiveAutoGame current = active;
		if (player == null || current == null || player.getServer() == null) return;
		ServerWorld world = player.getServer().getWorld(current.worldKey());
		if (world == null || player.getServerWorld() != world) return;
		int readyPlayers = GameFunctions.getReadyPlayerCount(world);
		int countdownSeconds = current.countdownTicks < 0 ? -1 : countdownSeconds(current.countdownTicks);
		PayloadSync.sendIfChanged(player, AutoGameStatusPayload.ID,
			new AutoGameStatusPayload(true, countdownSeconds, readyPlayers, MINIMUM_READY_PLAYERS));
	}

	private static void syncInactive(MinecraftServer server) {
		if (server == null) return;
		AutoGameStatusPayload payload = new AutoGameStatusPayload(false, -1, 0, MINIMUM_READY_PLAYERS);
		PayloadSync.forgetAll(AutoGameStatusPayload.ID);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			PayloadSync.sendIfChanged(player, AutoGameStatusPayload.ID, payload);
		}
	}

	private static final class ActiveAutoGame {
		private final Instant start;
		private final Instant end;
		@SuppressWarnings("unused")
		private final UUID creator;
		private final RegistryKey<World> worldKey;
		private boolean opened;
		private boolean waitingForRoundEnd;
		private int inactiveTicksAfterEnd;
		private int countdownTicks = -1;
		private int lastSyncedCountdownSeconds = Integer.MIN_VALUE;
		private int lastSyncedReadyPlayers = -1;

		private ActiveAutoGame(Instant start, Instant end, UUID creator, RegistryKey<World> worldKey) {
			this.start = start;
			this.end = end;
			this.creator = creator;
			this.worldKey = worldKey;
		}

		private Instant start() {
			return start;
		}

		private Instant end() {
			return end;
		}

		private RegistryKey<World> worldKey() {
			return worldKey;
		}

		private void resetCountdown() {
			countdownTicks = -1;
		}
	}
}
