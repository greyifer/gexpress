package dev.mapselect.command;

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
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.game.GexpressGameModes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.function.Predicate;

public final class StartCommand {
	private StartCommand() {}

	private static final Predicate<ServerCommandSource> OP_OR_HOST = GexpressPermissions::canUseHostCommands;

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("start")
			.requires(OP_OR_HOST)
			.then(mode("modded", () -> Harpymodloader.MODDED_GAMEMODE))
			.then(mode("amnesia", () -> GexpressGameModes.AMNESIA))
			.then(mode("takeover", () -> GexpressGameModes.TAKEOVER))
			.then(mode("discovery", () -> WatheGameModes.DISCOVERY))
			.then(mode("loose_ends", () -> WatheGameModes.LOOSE_ENDS))
			.then(mode("murder", () -> WatheGameModes.MURDER));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> mode(String literal, java.util.function.Supplier<GameMode> mode) {
		return CommandManager.literal(literal)
			.then(effect("generic", mode, () -> WatheMapEffects.GENERIC))
			.then(effect("day", mode, () -> WatheMapEffects.HARPY_EXPRESS_DAY))
			.then(effect("lobby", mode, () -> WatheMapEffects.HARPY_EXPRESS_LOBBY))
			.then(effect("night", mode, () -> WatheMapEffects.HARPY_EXPRESS_NIGHT))
			.then(effect("sundown", mode, () -> WatheMapEffects.HARPY_EXPRESS_SUNDOWN));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> effect(String literal,
			java.util.function.Supplier<GameMode> mode,
			java.util.function.Supplier<MapEffect> effect) {
		return CommandManager.literal(literal)
			.executes(ctx -> run(ctx, mode.get(), effect.get(), -1, 0))
			.then(minutesArg(mode, effect));
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Integer> minutesArg(
			java.util.function.Supplier<GameMode> mode,
			java.util.function.Supplier<MapEffect> effect) {
		return CommandManager.argument("minutes", IntegerArgumentType.integer(0))
			.executes(ctx -> run(ctx, mode.get(), effect.get(), IntegerArgumentType.getInteger(ctx, "minutes"), 0))
			.then(CommandManager.argument("seconds", IntegerArgumentType.integer(0, 59))
				.executes(ctx -> run(ctx, mode.get(), effect.get(),
					IntegerArgumentType.getInteger(ctx, "minutes"),
					IntegerArgumentType.getInteger(ctx, "seconds"))));
	}

	private static int run(CommandContext<ServerCommandSource> ctx, GameMode gameMode, MapEffect mapEffect, int minutes, int seconds) {
		ServerCommandSource src = ctx.getSource();
		if (gameMode == null || mapEffect == null) {
			src.sendError(Text.literal("That G'Express start option is not available."));
			return 0;
		}
		GameWorldComponent game = GameWorldComponent.KEY.get(src.getWorld());
		if (game.isRunning()) {
			src.sendError(Text.translatable("game.start_error.game_running"));
			return 0;
		}

		int resolvedMinutes = minutes >= 0 ? minutes : gameMode.defaultStartTime;
		int ticks = GameConstants.getInTicks(resolvedMinutes, seconds);
		Runnable start = () -> GameFunctions.startGame(src.getWorld(), gameMode, mapEffect, ticks);

		int result;
		if (requiresSupporterAccess(gameMode, mapEffect)) {
			result = Wathe.executeSupporterCommand(src, start);
		} else {
			start.run();
			result = 1;
		}

		if (result > 0) {
			src.sendFeedback(() -> Text.literal("Started G'Express game: ")
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
}
