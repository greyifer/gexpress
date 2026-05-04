package dev.mapselect.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.mapselect.network.GexpressPresetsSyncHandler;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.preset.train.TrainPreset;
import dev.mapselect.preset.train.TrainPresetStorage;
import dev.mapselect.weather.MapWeatherComponent;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class TrainCommand {

	private static final Predicate<ServerCommandSource> OP = GexpressPermissions::canUseAdminCommands;
	private static final Predicate<ServerCommandSource> OP_OR_HOST = GexpressPermissions::canUseHostCommands;

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		SuggestionProvider<ServerCommandSource> trainNames = TrainCommand::suggestTrainNames;

		return CommandManager.literal("train")
			.then(CommandManager.literal("preset")
				.then(CommandManager.literal("save")
					.requires(OP)
					.then(CommandManager.argument("name", StringArgumentType.word())
						.suggests(trainNames)
						.executes(ctx -> runSave(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(CommandManager.literal("delete")
					.requires(OP)
					.then(CommandManager.argument("name", StringArgumentType.word())
						.suggests(trainNames)
						.executes(ctx -> runDelete(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(CommandManager.literal("list")
					.requires(OP_OR_HOST)
					.executes(TrainCommand::runList))
				.then(CommandManager.literal("show")
					.requires(OP)
					.then(CommandManager.argument("name", StringArgumentType.word())
						.suggests(trainNames)
						.executes(ctx -> runShow(ctx, StringArgumentType.getString(ctx, "name"))))));
	}

	static CompletableFuture<Suggestions> suggestTrainNames(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
		try {
			List<String> names = TrainPresetStorage.list(ctx.getSource().getServer());
			String remaining = builder.getRemainingLowerCase();
			for (String n : names) {
				if (n.toLowerCase().startsWith(remaining)) builder.suggest(n);
			}
		} catch (IOException ignored) {
		}
		return builder.buildFuture();
	}

	private static int runSave(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		if (!TrainPresetStorage.isValidName(name)) {
			src.sendError(Text.literal("Invalid preset name. Use letters, digits, '_' or '-', up to 64 chars."));
			return 0;
		}
		try {
			TrainPreset preset = TrainPreset.from(src.getWorld());
			if (preset.resetTemplateArea == null) {
				src.sendError(Text.literal("resetTemplateArea is not set. Use /wathe mapVariables set resetTemplateArea first."));
				return 0;
			}
			TrainPresetStorage.save(src.getServer(), name, preset);
			GexpressPresetsSyncHandler.broadcastTrainPresets(src.getServer());

			String currentMap = MapWeatherComponent.KEY.get(src.getWorld()).getCurrentMapName();
			String suffix = "";
			if (currentMap != null && PresetStorage.exists(src.getServer(), currentMap)) {
				MapPreset mp = PresetStorage.load(src.getServer(), currentMap);
				if (mp != null) {
					mp.defaultTrainPreset = name;
					PresetStorage.save(src.getServer(), currentMap, mp);
					GexpressPresetsSyncHandler.broadcastPresets(src.getServer());
					suffix = " — associated as default for map '" + currentMap + "'.";
				}
			}

			final int slots = preset.slotCount();
			final int carts = preset.cartCount();
			final String s = suffix;
			src.sendFeedback(() -> Text.literal("Saved train preset '" + name + "' (" + slots + " TP slot" + (slots == 1 ? "" : "s")
				+ ", " + carts + " cart" + (carts == 1 ? "" : "s") + ")." + s).formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to save train preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runDelete(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		if (!TrainPresetStorage.isValidName(name)) {
			src.sendError(Text.literal("Invalid preset name."));
			return 0;
		}
		try {
			boolean deleted = TrainPresetStorage.delete(src.getServer(), name);
			if (!deleted) {
				src.sendError(Text.literal("Train preset '" + name + "' does not exist."));
				return 0;
			}
			int clearedDefaults = clearDefaultTrainReferences(src, name);
			if (clearedDefaults > 0) {
				GexpressPresetsSyncHandler.broadcastPresets(src.getServer());
			}
			GexpressPresetsSyncHandler.broadcastTrainPresets(src.getServer());
			final int cleared = clearedDefaults;
			src.sendFeedback(() -> {
				Text base = Text.literal("Deleted train preset '" + name + "'.").formatted(Formatting.YELLOW);
				if (cleared > 0) {
					return base.copy().append(Text.literal(" Cleared it from " + cleared + " map preset(s).").formatted(Formatting.GRAY));
				}
				return base;
			}, true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to delete train preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int clearDefaultTrainReferences(ServerCommandSource src, String trainName) throws IOException {
		int cleared = 0;
		for (String mapName : PresetStorage.list(src.getServer())) {
			MapPreset preset = PresetStorage.load(src.getServer(), mapName);
			if (preset == null || !trainName.equals(preset.defaultTrainPreset)) continue;
			preset.defaultTrainPreset = null;
			PresetStorage.save(src.getServer(), mapName, preset);
			cleared++;
		}
		return cleared;
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		try {
			List<String> names = TrainPresetStorage.list(src.getServer());
			if (names.isEmpty()) {
				src.sendFeedback(() -> Text.literal("No train presets. Use /g setup train preset save <name>."), false);
				return 1;
			}
			String joined = String.join(", ", names);
			src.sendFeedback(() -> Text.literal("Train presets (" + names.size() + "): " + joined), false);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to list train presets: " + e.getMessage()));
			return 0;
		}
	}

	private static int runShow(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		if (!TrainPresetStorage.isValidName(name)) {
			src.sendError(Text.literal("Invalid preset name."));
			return 0;
		}
		try {
			TrainPreset preset = TrainPresetStorage.load(src.getServer(), name);
			if (preset == null) {
				src.sendError(Text.literal("Train preset '" + name + "' does not exist."));
				return 0;
			}
			src.sendFeedback(() -> Text.literal("─── Train '" + name + "' ───").formatted(Formatting.GOLD, Formatting.BOLD), false);
			src.sendFeedback(() -> Text.literal("Template Area: ").formatted(Formatting.AQUA)
				.append(Text.literal(boxStr(preset.resetTemplateArea)).formatted(Formatting.WHITE)), false);
			src.sendFeedback(() -> Text.literal("Paste Offset: ").formatted(Formatting.AQUA)
				.append(Text.literal(offsetStr(preset.resetPasteOffset)).formatted(Formatting.WHITE)), false);
			final int slots = preset.slotCount();
			src.sendFeedback(() -> Text.literal("TP Slots: ").formatted(Formatting.AQUA)
				.append(Text.literal(Integer.toString(slots)).formatted(Formatting.WHITE)), false);
			final int carts = preset.cartCount();
			src.sendFeedback(() -> Text.literal("Train Carts: ").formatted(Formatting.AQUA)
				.append(Text.literal(Integer.toString(carts)).formatted(Formatting.WHITE)), false);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to load train preset: " + e.getMessage()));
			return 0;
		}
	}

	private static String boxStr(MapPreset.BoxData b) {
		if (b == null) return "(not set)";
		return "(" + fmt(b.minX) + ", " + fmt(b.minY) + ", " + fmt(b.minZ) + ") → ("
			+ fmt(b.maxX) + ", " + fmt(b.maxY) + ", " + fmt(b.maxZ) + ")";
	}

	private static String offsetStr(MapPreset.OffsetData o) {
		if (o == null) return "(not set)";
		return "(" + o.x + ", " + o.y + ", " + o.z + ")";
	}

	private static String fmt(double d) {
		if (d == Math.floor(d) && !Double.isInfinite(d)) return Long.toString((long) d);
		return String.format(java.util.Locale.ROOT, "%.1f", d);
	}
}
