package dev.mapselect.command.setup;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.mapselect.network.preset.GexpressPresetsSyncHandler;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.preset.train.TrainPreset;
import dev.mapselect.preset.train.TrainPresetStorage;
import dev.mapselect.preset.train.TrainPresetValidator;
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

	private static final Predicate<ServerCommandSource> OP = source ->
		GexpressPermissions.canUseTrainCommands(source)
			|| GexpressPermissions.canUseCommandBranch(source, "setup", "train");

	private static Predicate<ServerCommandSource> trainCommand(String... subcommand) {
		return source -> GexpressPermissions.canUseTrainCommands(source)
			|| GexpressPermissions.canUseCommandPath(source, trainPath(subcommand));
	}

	private static String[] trainPath(String... subcommand) {
		String[] path = new String[(subcommand == null ? 0 : subcommand.length) + 2];
		path[0] = "setup";
		path[1] = "train";
		if (subcommand != null) System.arraycopy(subcommand, 0, path, 2, subcommand.length);
		return path;
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		SuggestionProvider<ServerCommandSource> trainNames = TrainCommand::suggestTrainNames;

		return CommandManager.literal("train")
			.then(CommandManager.literal("preset")
				.then(CommandManager.literal("save")
					.requires(trainCommand("preset", "save"))
					.then(CommandManager.argument("name", StringArgumentType.word())
						.suggests(trainNames)
						.executes(ctx -> runSave(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(CommandManager.literal("delete")
					.requires(trainCommand("preset", "delete"))
					.then(CommandManager.argument("name", StringArgumentType.word())
						.suggests(trainNames)
						.executes(ctx -> runDelete(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(CommandManager.literal("duplicate")
					.requires(trainCommand("preset", "duplicate"))
					.then(CommandManager.argument("source", StringArgumentType.word())
						.suggests(trainNames)
						.then(CommandManager.argument("target", StringArgumentType.word())
							.executes(ctx -> runDuplicate(ctx,
								StringArgumentType.getString(ctx, "source"),
								StringArgumentType.getString(ctx, "target"))))))
				
				.then(CommandManager.literal("backup")
					.requires(trainCommand("preset", "backup"))
					.then(CommandManager.argument("name", StringArgumentType.word())
						.suggests(trainNames)
						.then(CommandManager.literal("list")
							.executes(ctx -> runBackupList(ctx, StringArgumentType.getString(ctx, "name"))))
						.then(CommandManager.literal("restore")
							.then(CommandManager.argument("backupId", StringArgumentType.word())
								.suggests(TrainCommand::suggestTrainBackupIds)
								.then(CommandManager.literal("confirm")
									.executes(ctx -> runBackupRestore(ctx,
										StringArgumentType.getString(ctx, "name"),
										StringArgumentType.getString(ctx, "backupId"))))))))
				.then(CommandManager.literal("rename")
					.requires(trainCommand("preset", "rename"))
					.then(CommandManager.argument("oldName", StringArgumentType.word())
						.suggests(trainNames)
						.then(CommandManager.argument("newName", StringArgumentType.word())
							.then(CommandManager.literal("confirm")
								.executes(ctx -> runRename(ctx,
									StringArgumentType.getString(ctx, "oldName"),
									StringArgumentType.getString(ctx, "newName")))))))
				.then(CommandManager.literal("validate")
					.requires(trainCommand("preset", "validate"))
					.executes(TrainCommand::runValidateAll)
					.then(CommandManager.literal("issues")
						.executes(TrainCommand::runValidateIssues))
					.then(CommandManager.argument("name", StringArgumentType.word())
						.suggests(trainNames)
						.executes(ctx -> runValidate(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(CommandManager.literal("list")
					.requires(trainCommand("preset", "list"))
					.executes(TrainCommand::runList))
				.then(CommandManager.literal("show")
					.requires(trainCommand("preset", "show"))
					.then(CommandManager.argument("name", StringArgumentType.word())
						.suggests(trainNames)
						.executes(ctx -> runShow(ctx, StringArgumentType.getString(ctx, "name"))))));
	}

	public static CompletableFuture<Suggestions> suggestTrainNames(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
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

	private static CompletableFuture<Suggestions> suggestTrainBackupIds(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
		try {
			String name = StringArgumentType.getString(ctx, "name");
			String remaining = builder.getRemainingLowerCase();
			for (String id : TrainPresetStorage.listBackups(ctx.getSource().getServer(), name)) {
				if (id.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) builder.suggest(id);
			}
		} catch (Exception ignored) {
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
			TrainPreset existing = TrainPresetStorage.exists(src.getServer(), name)
				? TrainPresetStorage.load(src.getServer(), name)
				: null;
			if ((preset.trainCarts == null || preset.trainCarts.isEmpty())
					&& existing != null && existing.trainCarts != null && !existing.trainCarts.isEmpty()) {
				preset.trainCarts = copyTrainCarts(existing.trainCarts);
			}
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
					MapPreset active = MapPreset.from(src.getWorld());
					mp.defaultTrainPreset = name;
					mp.readyAreaSpawnPos = active.readyAreaSpawnPos;
					mp.randomSpawnPositions = active.randomSpawnPositions;
					PresetStorage.save(src.getServer(), currentMap, mp);
					GexpressPresetsSyncHandler.broadcastPresets(src.getServer());
					suffix = " — associated as default for map '" + currentMap + "'.";
				}
			}

			final int slots = preset.slotCount();
			final int carts = preset.cartCount();
			final String s = suffix;
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Saved train preset '" + name + "' (" + slots + " TP slot" + (slots == 1 ? "" : "s")
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
			dev.mapselect.command.GexpressFeedback.send(src, () -> {
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

	private static int runDuplicate(CommandContext<ServerCommandSource> ctx, String sourceName, String targetName) {
		ServerCommandSource src = ctx.getSource();
		if (!TrainPresetStorage.isValidName(sourceName) || !TrainPresetStorage.isValidName(targetName)) {
			src.sendError(Text.literal("Invalid train preset name. Use letters, digits, '_' or '-', up to 64 chars."));
			return 0;
		}
		try {
			TrainPresetStorage.duplicate(src.getServer(), sourceName, targetName);
			GexpressPresetsSyncHandler.broadcastTrainPresets(src.getServer());
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Duplicated train preset '" + sourceName
				+ "' to '" + targetName + "'.").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal(e.getMessage()));
			return 0;
		}
	}

	private static int runBackupList(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		try {
			List<String> backups = TrainPresetStorage.listBackups(src.getServer(), name);
			if (backups.isEmpty()) {
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("No backups found for train preset '" + name + "'."), false);
				return 1;
			}
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Backups for train preset '" + name + "': "
				+ String.join(", ", backups)), false);
			return backups.size();
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to list train preset backups: " + e.getMessage()));
			return 0;
		}
	}

	private static int runBackupRestore(CommandContext<ServerCommandSource> ctx, String name, String backupId) {
		ServerCommandSource src = ctx.getSource();
		try {
			TrainPresetStorage.restoreBackup(src.getServer(), name, backupId);
			GexpressPresetsSyncHandler.broadcastTrainPresets(src.getServer());
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Restored train preset '" + name
				+ "' from backup " + backupId + ".").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to restore train preset backup: " + e.getMessage()));
			return 0;
		}
	}

	private static int runRename(CommandContext<ServerCommandSource> ctx, String oldName, String newName) {
		ServerCommandSource src = ctx.getSource();
		if (!TrainPresetStorage.isValidName(oldName) || !TrainPresetStorage.isValidName(newName)) {
			src.sendError(Text.literal("Invalid train preset name. Use letters, digits, '_' or '-', up to 64 chars."));
			return 0;
		}
		if (oldName.equals(newName)) {
			src.sendError(Text.literal("New name is the same as the old name."));
			return 0;
		}
		try {
			TrainPresetStorage.rename(src.getServer(), oldName, newName);
			int updatedDefaults = updateDefaultTrainReferences(src, oldName, newName);
			if (updatedDefaults > 0) {
				GexpressPresetsSyncHandler.broadcastPresets(src.getServer());
			}
			GexpressPresetsSyncHandler.broadcastTrainPresets(src.getServer());
			final int updated = updatedDefaults;
			dev.mapselect.command.GexpressFeedback.send(src, () -> {
				Text base = Text.literal("Renamed train preset '" + oldName + "' to '" + newName + "'.").formatted(Formatting.GREEN);
				if (updated > 0) {
					return base.copy().append(Text.literal(" Updated " + updated + " map preset default(s).").formatted(Formatting.GRAY));
				}
				return base;
			}, true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal(e.getMessage()));
			return 0;
		}
	}

	private static int runValidate(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		try {
			TrainPreset preset = TrainPresetStorage.load(src.getServer(), name);
			List<TrainPresetValidator.Issue> issues = TrainPresetValidator.validate(name, preset);
			return sendValidation(src, name, issues);
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to validate train preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runValidateAll(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		try {
			List<String> names = TrainPresetStorage.list(src.getServer());
			if (names.isEmpty()) {
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("No train presets to validate."), false);
				return 1;
			}
			int errors = 0;
			int warnings = 0;
			for (String name : names) {
				TrainPreset preset = TrainPresetStorage.load(src.getServer(), name);
				List<TrainPresetValidator.Issue> issues = TrainPresetValidator.validate(name, preset);
				long mapErrors = issues.stream().filter(issue -> issue.severity() == TrainPresetValidator.Severity.ERROR).count();
				long mapWarnings = issues.stream().filter(issue -> issue.severity() == TrainPresetValidator.Severity.WARNING).count();
				errors += (int) mapErrors;
				warnings += (int) mapWarnings;
				Formatting color = mapErrors > 0 ? Formatting.RED : (mapWarnings > 0 ? Formatting.YELLOW : Formatting.GREEN);
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(" - " + name + ": "
					+ mapErrors + " error(s), " + mapWarnings + " warning(s)").formatted(color), false);
			}
			final int totalErrors = errors;
			final int totalWarnings = warnings;
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Train validation: " + names.size()
				+ " preset(s), " + totalErrors + " error(s), " + totalWarnings + " warning(s).")
				.formatted(totalErrors > 0 ? Formatting.RED : Formatting.GREEN), false);
			return totalErrors == 0 ? 1 : 0;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to validate train presets: " + e.getMessage()));
			return 0;
		}
	}

	private static int runValidateIssues(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		try {
			List<String> names = TrainPresetStorage.list(src.getServer());
			if (names.isEmpty()) {
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("No train presets to validate."), false);
				return 1;
			}
			int errors = 0;
			int warnings = 0;
			int presetsWithIssues = 0;
			for (String name : names) {
				TrainPreset preset = TrainPresetStorage.load(src.getServer(), name);
				List<TrainPresetValidator.Issue> issues = TrainPresetValidator.validate(name, preset);
				long presetErrors = issues.stream().filter(issue -> issue.severity() == TrainPresetValidator.Severity.ERROR).count();
				long presetWarnings = issues.stream().filter(issue -> issue.severity() == TrainPresetValidator.Severity.WARNING).count();
				if (presetErrors == 0 && presetWarnings == 0) continue;
				presetsWithIssues++;
				errors += (int) presetErrors;
				warnings += (int) presetWarnings;
				Formatting color = presetErrors > 0 ? Formatting.RED : Formatting.YELLOW;
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(" - " + name + ": "
					+ presetErrors + " error(s), " + presetWarnings + " warning(s)").formatted(color), false);
			}
			if (presetsWithIssues == 0) {
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Train validation: no issues across "
					+ names.size() + " preset(s).").formatted(Formatting.GREEN), false);
				return 1;
			}
			final int issueCount = presetsWithIssues;
			final int totalErrors = errors;
			final int totalWarnings = warnings;
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Train validation issues: " + issueCount + "/"
				+ names.size() + " preset(s), " + totalErrors + " error(s), " + totalWarnings + " warning(s).")
				.formatted(totalErrors > 0 ? Formatting.RED : Formatting.YELLOW), false);
			return totalErrors == 0 ? 1 : 0;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to validate train presets: " + e.getMessage()));
			return 0;
		}
	}

	private static int sendValidation(ServerCommandSource src, String name, List<TrainPresetValidator.Issue> issues) {
		long errors = issues.stream().filter(issue -> issue.severity() == TrainPresetValidator.Severity.ERROR).count();
		long warnings = issues.stream().filter(issue -> issue.severity() == TrainPresetValidator.Severity.WARNING).count();
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Train preset '" + name + "' validation: "
			+ errors + " error(s), " + warnings + " warning(s).").formatted(errors > 0 ? Formatting.RED : Formatting.GREEN), false);
		for (TrainPresetValidator.Issue issue : issues) {
			Formatting color = issue.severity() == TrainPresetValidator.Severity.ERROR ? Formatting.RED : Formatting.YELLOW;
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(" - " + issue.severity().name().toLowerCase(java.util.Locale.ROOT)
				+ ": " + issue.message()).formatted(color), false);
		}
		return errors == 0 ? 1 : 0;
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

	private static int updateDefaultTrainReferences(ServerCommandSource src, String oldTrainName, String newTrainName) throws IOException {
		int updated = 0;
		for (String mapName : PresetStorage.list(src.getServer())) {
			MapPreset preset = PresetStorage.load(src.getServer(), mapName);
			if (preset == null || !oldTrainName.equals(preset.defaultTrainPreset)) continue;
			preset.defaultTrainPreset = newTrainName;
			PresetStorage.save(src.getServer(), mapName, preset);
			updated++;
		}
		return updated;
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		try {
			List<String> names = TrainPresetStorage.list(src.getServer());
			if (names.isEmpty()) {
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("No train presets. Use /g setup train preset save <name>."), false);
				return 1;
			}
			String joined = String.join(", ", names);
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Train presets (" + names.size() + "): " + joined), false);
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
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("─── Train '" + name + "' ───").formatted(Formatting.GOLD, Formatting.BOLD), false);
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Template Area: ").formatted(Formatting.AQUA)
				.append(Text.literal(boxStr(preset.resetTemplateArea)).formatted(Formatting.WHITE)), false);
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Paste Offset: ").formatted(Formatting.AQUA)
				.append(Text.literal(offsetStr(preset.resetPasteOffset)).formatted(Formatting.WHITE)), false);
			final int slots = preset.slotCount();
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("TP Slots: ").formatted(Formatting.AQUA)
				.append(Text.literal(Integer.toString(slots)).formatted(Formatting.WHITE)), false);
			final int carts = preset.cartCount();
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Train Carts: ").formatted(Formatting.AQUA)
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

	private static List<TrainPreset.CartData> copyTrainCarts(List<TrainPreset.CartData> carts) {
		List<TrainPreset.CartData> out = new java.util.ArrayList<>();
		if (carts == null) return out;
		for (TrainPreset.CartData cart : carts) {
			if (cart == null || cart.area == null) continue;
			TrainPreset.CartData copy = new TrainPreset.CartData();
			copy.area = new MapPreset.BoxData();
			copy.area.minX = cart.area.minX;
			copy.area.minY = cart.area.minY;
			copy.area.minZ = cart.area.minZ;
			copy.area.maxX = cart.area.maxX;
			copy.area.maxY = cart.area.maxY;
			copy.area.maxZ = cart.area.maxZ;
			out.add(copy);
		}
		return out;
	}

	private static String fmt(double d) {
		if (d == Math.floor(d) && !Double.isInfinite(d)) return Long.toString((long) d);
		return String.format(java.util.Locale.ROOT, "%.1f", d);
	}
}
