package dev.mapselect.command.game;

import cat.rezelyn.watheextended.cca.WatheExtendedWorldComponent;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.doctor4t.wathe.Wathe;
import dev.doctor4t.wathe.api.GameMode;
import dev.doctor4t.wathe.api.MapEffect;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.api.WatheMapEffects;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.game.GexpressGameModes;
import dev.mapselect.game.draft.DraftManager;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.MapPresetValidator;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.preset.train.TrainPreset;
import dev.mapselect.preset.train.TrainPresetStorage;
import dev.mapselect.preset.train.TrainPresetValidator;
import dev.mapselect.weather.MapWeatherComponent;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.harpymodloader.Harpymodloader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class StartCommand {
	private StartCommand() {}

	private static final Predicate<ServerCommandSource> CAN_START_GAMES = GexpressPermissions::canStartGames;

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("start")
			.requires(CAN_START_GAMES)
			.then(CommandManager.literal("dryrun")
				.then(mode("modded", () -> Harpymodloader.MODDED_GAMEMODE, true))
				.then(mode("amnesia", () -> GexpressGameModes.AMNESIA, true))
				.then(mode("takeover", () -> GexpressGameModes.TAKEOVER, true))
				.then(mode("draft", () -> GexpressGameModes.DRAFT, true))
				.then(mode("test", () -> GexpressGameModes.TEST, true))
				.then(mode("discovery", () -> WatheGameModes.DISCOVERY, true))
				.then(mode("loose_ends", () -> WatheGameModes.LOOSE_ENDS, true))
				.then(mode("murder", () -> WatheGameModes.MURDER, true)))
			.then(mode("modded", () -> Harpymodloader.MODDED_GAMEMODE))
			.then(mode("amnesia", () -> GexpressGameModes.AMNESIA))
			.then(mode("takeover", () -> GexpressGameModes.TAKEOVER))
			.then(mode("draft", () -> GexpressGameModes.DRAFT))
			.then(mode("test", () -> GexpressGameModes.TEST))
			.then(mode("discovery", () -> WatheGameModes.DISCOVERY))
			.then(mode("loose_ends", () -> WatheGameModes.LOOSE_ENDS))
			.then(mode("murder", () -> WatheGameModes.MURDER));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> mode(String literal, java.util.function.Supplier<GameMode> mode) {
		return mode(literal, mode, false);
	}

	private static LiteralArgumentBuilder<ServerCommandSource> mode(String literal, java.util.function.Supplier<GameMode> mode, boolean dryRun) {
		return CommandManager.literal(literal)
			.then(effect("generic", mode, () -> WatheMapEffects.GENERIC, dryRun))
			.then(effect("day", mode, () -> WatheMapEffects.HARPY_EXPRESS_DAY, dryRun))
			.then(effect("lobby", mode, () -> WatheMapEffects.HARPY_EXPRESS_LOBBY, dryRun))
			.then(effect("night", mode, () -> WatheMapEffects.HARPY_EXPRESS_NIGHT, dryRun))
			.then(effect("sundown", mode, () -> WatheMapEffects.HARPY_EXPRESS_SUNDOWN, dryRun));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> effect(String literal,
			java.util.function.Supplier<GameMode> mode,
			java.util.function.Supplier<MapEffect> effect) {
		return effect(literal, mode, effect, false);
	}

	private static LiteralArgumentBuilder<ServerCommandSource> effect(String literal,
			java.util.function.Supplier<GameMode> mode,
			java.util.function.Supplier<MapEffect> effect,
			boolean dryRun) {
		return CommandManager.literal(literal)
			.executes(ctx -> run(ctx, mode.get(), effect.get(), -1, 0, dryRun))
			.then(minutesArg(mode, effect, dryRun));
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Integer> minutesArg(
			java.util.function.Supplier<GameMode> mode,
			java.util.function.Supplier<MapEffect> effect) {
		return minutesArg(mode, effect, false);
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Integer> minutesArg(
			java.util.function.Supplier<GameMode> mode,
			java.util.function.Supplier<MapEffect> effect,
			boolean dryRun) {
		return CommandManager.argument("minutes", IntegerArgumentType.integer(0))
			.executes(ctx -> run(ctx, mode.get(), effect.get(), IntegerArgumentType.getInteger(ctx, "minutes"), 0, dryRun))
			.then(CommandManager.argument("seconds", IntegerArgumentType.integer(0, 59))
				.executes(ctx -> run(ctx, mode.get(), effect.get(),
					IntegerArgumentType.getInteger(ctx, "minutes"),
					IntegerArgumentType.getInteger(ctx, "seconds"),
					dryRun)));
	}

	private static int run(CommandContext<ServerCommandSource> ctx, GameMode gameMode, MapEffect mapEffect, int minutes, int seconds) {
		return run(ctx, gameMode, mapEffect, minutes, seconds, false);
	}

	private static int run(CommandContext<ServerCommandSource> ctx, GameMode gameMode, MapEffect mapEffect, int minutes, int seconds,
			boolean dryRun) {
		ServerCommandSource src = ctx.getSource();
		if (gameMode == null || mapEffect == null) {
			src.sendError(Text.literal("That G'Express start option is not available."));
			return 0;
		}
		GameWorldComponent game = GameWorldComponent.KEY.get(src.getWorld());
		if (game.isRunning() || DraftManager.isDrafting(src.getWorld())) {
			src.sendError(Text.translatable("game.start_error.game_running"));
			return 0;
		}

		int resolvedMinutes = minutes >= 0 ? minutes : defaultStartMinutes(gameMode);
		int ticks = GameConstants.getInTicks(resolvedMinutes, seconds);
		List<PreflightIssue> preflight = collectPreflight(src);
		if (dryRun) {
			int online = src.getServer().getPlayerManager().getCurrentPlayerCount();
			boolean supporter = requiresSupporterAccess(gameMode, mapEffect);
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Dry run: would start G'Express game: ")
				.formatted(Formatting.YELLOW)
				.append(Text.literal(label(gameMode) + " / " + label(mapEffect)).formatted(Formatting.WHITE))
				.append(Text.literal(" at " + resolvedMinutes + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds)).formatted(Formatting.GRAY))
				.append(Text.literal(", ticks=" + ticks + ", online=" + online
					+ (supporter ? ", supporter-gated" : "")).formatted(Formatting.DARK_GRAY)), false);
			sendPreflight(src, preflight);
			return 1;
		}
		if (hasPreflightErrors(preflight)) {
			sendPreflight(src, preflight);
			src.sendError(Text.literal("Start blocked by G'Express setup preflight errors."));
			return 0;
		}
		if (!preflight.isEmpty()) {
			sendPreflight(src, preflight);
		}
		Runnable start = () -> GameFunctions.startGame(src.getWorld(), gameMode, mapEffect, ticks);

		int result;
		if (requiresSupporterAccess(gameMode, mapEffect)) {
			result = Wathe.executeSupporterCommand(src, start);
		} else {
			start.run();
			result = 1;
		}

		if (result > 0) {
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Started G'Express game: ")
				.formatted(Formatting.GREEN)
				.append(Text.literal(label(gameMode) + " / " + label(mapEffect)).formatted(Formatting.WHITE))
				.append(Text.literal(" at " + resolvedMinutes + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds)).formatted(Formatting.GRAY)), true);
		}
		return result;
	}

	private static boolean requiresSupporterAccess(GameMode gameMode, MapEffect mapEffect) {
		return gameMode == WatheGameModes.LOOSE_ENDS
			|| gameMode == WatheGameModes.DISCOVERY
			|| mapEffect == WatheMapEffects.HARPY_EXPRESS_SUNDOWN
			|| mapEffect == WatheMapEffects.HARPY_EXPRESS_DAY;
	}

	private static int defaultStartMinutes(GameMode gameMode) {
		return GexpressGameModes.isTest(gameMode)
			? GexpressGameModes.TEST_DEFAULT_START_MINUTES
			: gameMode.defaultStartTime;
	}

	private static String label(GameMode mode) {
		return shortId(mode.identifier.toString());
	}

	private static String label(MapEffect effect) {
		return shortId(effect.identifier.toString());
	}

	private static String shortId(String id) {
		int colon = id.indexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}

	private static List<PreflightIssue> collectPreflight(ServerCommandSource src) {
		List<PreflightIssue> issues = new ArrayList<>();
		try {
			MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(src.getWorld());
			String activeMapName = weather == null ? null : weather.getCurrentMapName();
			MapPreset activeMap = null;
			if (activeMapName == null || activeMapName.isBlank()) {
				issues.add(PreflightIssue.error("No active map preset is selected."));
			} else if (!PresetStorage.exists(src.getServer(), activeMapName)) {
				issues.add(PreflightIssue.error("Active map preset '" + activeMapName + "' does not exist."));
			} else {
				activeMap = PresetStorage.load(src.getServer(), activeMapName);
				addMapIssues(issues, activeMapName, MapPresetValidator.validate(src.getServer(), activeMapName, activeMap));
				addDefaultTrainIssues(src, issues, activeMap);
			}
			addSpawnIssues(src, issues, activeMap);
		} catch (IOException error) {
			issues.add(PreflightIssue.error("Setup preflight failed: " + error.getMessage()));
		}
		return List.copyOf(issues);
	}

	private static void addDefaultTrainIssues(ServerCommandSource src, List<PreflightIssue> issues, MapPreset activeMap) throws IOException {
		if (activeMap == null || activeMap.defaultTrainPreset == null || activeMap.defaultTrainPreset.isBlank()
				|| !TrainPresetStorage.exists(src.getServer(), activeMap.defaultTrainPreset)) {
			return;
		}
		String trainName = activeMap.defaultTrainPreset;
		TrainPreset train = TrainPresetStorage.load(src.getServer(), trainName);
		addTrainIssues(issues, trainName, TrainPresetValidator.validate(trainName, train));
	}

	private static void addSpawnIssues(ServerCommandSource src, List<PreflightIssue> issues, MapPreset activeMap) {
		int online = src.getServer().getPlayerManager().getCurrentPlayerCount();
		int savedSpawns = activeMap == null || activeMap.randomSpawnPositions == null ? 0 : activeMap.randomSpawnPositions.size();
		if (savedSpawns > 0 && online > savedSpawns) {
			issues.add(PreflightIssue.warning("Online players (" + online + ") exceed saved map spawns (" + savedSpawns + ")."));
		}

		WatheExtendedWorldComponent component = WatheExtendedWorldComponent.KEY.getNullable(src.getWorld());
		Map<?, ?> slots = component == null || component.getTeleportationSlots() == null
			? Map.of()
			: component.getTeleportationSlots();
		int liveSlots = slots.size();
		if (component == null || !component.isRtpEnabled()) {
			issues.add(PreflightIssue.warning("RTP is disabled for this world."));
		}
		if (liveSlots == 0) {
			issues.add(PreflightIssue.warning("No live RTP slots are configured."));
		} else if (online > liveSlots) {
			issues.add(PreflightIssue.warning("Online players (" + online + ") exceed live RTP slots (" + liveSlots + ")."));
		}
	}

	private static void addMapIssues(List<PreflightIssue> target, String name, List<MapPresetValidator.Issue> issues) {
		for (MapPresetValidator.Issue issue : issues) {
			target.add(new PreflightIssue(
				issue.severity() == MapPresetValidator.Severity.ERROR ? PreflightSeverity.ERROR : PreflightSeverity.WARNING,
				"Map '" + name + "': " + issue.message()));
		}
	}

	private static void addTrainIssues(List<PreflightIssue> target, String name, List<TrainPresetValidator.Issue> issues) {
		for (TrainPresetValidator.Issue issue : issues) {
			target.add(new PreflightIssue(
				issue.severity() == TrainPresetValidator.Severity.ERROR ? PreflightSeverity.ERROR : PreflightSeverity.WARNING,
				"Train '" + name + "': " + issue.message()));
		}
	}

	private static boolean hasPreflightErrors(List<PreflightIssue> issues) {
		return issues.stream().anyMatch(issue -> issue.severity() == PreflightSeverity.ERROR);
	}

	private static void sendPreflight(ServerCommandSource src, List<PreflightIssue> issues) {
		if (issues.isEmpty()) {
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Preflight: no setup issues found.")
				.formatted(Formatting.GREEN), false);
			return;
		}
		long errors = issues.stream().filter(issue -> issue.severity() == PreflightSeverity.ERROR).count();
		long warnings = issues.size() - errors;
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Preflight: " + errors + " error(s), " + warnings + " warning(s).")
			.formatted(errors > 0 ? Formatting.RED : Formatting.YELLOW), false);
		int limit = Math.min(8, issues.size());
		for (int i = 0; i < limit; i++) {
			PreflightIssue issue = issues.get(i);
			Formatting color = issue.severity() == PreflightSeverity.ERROR ? Formatting.RED : Formatting.YELLOW;
			String marker = issue.severity() == PreflightSeverity.ERROR ? "[ERROR] " : "[WARN] ";
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(marker + issue.message()).formatted(color), false);
		}
		if (issues.size() > limit) {
			int more = issues.size() - limit;
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Preflight: " + more + " more issue(s). Run /g setup checklist for the full setup pass.")
				.formatted(Formatting.GRAY), false);
		}
	}

	private record PreflightIssue(PreflightSeverity severity, String message) {
		static PreflightIssue error(String message) {
			return new PreflightIssue(PreflightSeverity.ERROR, message);
		}

		static PreflightIssue warning(String message) {
			return new PreflightIssue(PreflightSeverity.WARNING, message);
		}
	}

	private enum PreflightSeverity {
		ERROR,
		WARNING
	}
}
