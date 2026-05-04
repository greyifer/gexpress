package dev.mapselect.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
import dev.mapselect.preset.train.TrainPreview;
import dev.mapselect.weather.MapWeatherComponent;
import dev.mapselect.weather.WeatherType;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class MapSelectCommand {

	private static final Predicate<ServerCommandSource> OP = GexpressPermissions::canUseAdminCommands;
	private static final Predicate<ServerCommandSource> OP_OR_HOST = GexpressPermissions::canUseHostCommands;

	private enum BoxKind {
		WHOLE_MAP("whole map area"),
		PLAY_AREA("play area"),
		TEMPLATE("template area");

		final String label;
		BoxKind(String label) { this.label = label; }
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildMapTree() {
		SuggestionProvider<ServerCommandSource> nameSuggestions = MapSelectCommand::suggestPresetNames;

		return CommandManager.literal("map")
			.then(CommandManager.literal("create")
				.requires(OP)
				.then(CommandManager.argument("corner1", BlockPosArgumentType.blockPos())
					.then(CommandManager.argument("corner2", BlockPosArgumentType.blockPos())
						.then(CommandManager.argument("name", StringArgumentType.word())
							.executes(ctx -> runCreate(ctx,
								BlockPosArgumentType.getBlockPos(ctx, "corner1"),
								BlockPosArgumentType.getBlockPos(ctx, "corner2"),
								StringArgumentType.getString(ctx, "name")))))))
			.then(CommandManager.literal("delete")
				.requires(OP)
				.then(CommandManager.argument("name", StringArgumentType.word())
					.suggests(nameSuggestions)
					.executes(ctx -> runDelete(ctx, StringArgumentType.getString(ctx, "name")))))
			.then(CommandManager.literal("edit")
				.requires(OP)
				.then(CommandManager.argument("name", StringArgumentType.word())
					.suggests(nameSuggestions)
					.then(CommandManager.literal("corners")
						.then(CommandManager.argument("corner1", BlockPosArgumentType.blockPos())
							.suggests(suggestFromPreset(p -> p.wholeMapArea == null ? null :
								fmtNum(p.wholeMapArea.minX) + " " + fmtNum(p.wholeMapArea.minY) + " " + fmtNum(p.wholeMapArea.minZ)))
							.then(CommandManager.argument("corner2", BlockPosArgumentType.blockPos())
								.suggests(suggestFromPreset(p -> p.wholeMapArea == null ? null :
									fmtNum(p.wholeMapArea.maxX) + " " + fmtNum(p.wholeMapArea.maxY) + " " + fmtNum(p.wholeMapArea.maxZ)))
								.executes(ctx -> runEditCorners(ctx,
									StringArgumentType.getString(ctx, "name"),
									BlockPosArgumentType.getBlockPos(ctx, "corner1"),
									BlockPosArgumentType.getBlockPos(ctx, "corner2"))))))
					.then(CommandManager.literal("rename")
						.then(CommandManager.argument("newName", StringArgumentType.word())
							.suggests((c, b) -> {
								b.suggest(StringArgumentType.getString(c, "name"));
								return b.buildFuture();
							})
							.executes(ctx -> runEditRename(ctx,
								StringArgumentType.getString(ctx, "name"),
								StringArgumentType.getString(ctx, "newName")))))
					.then(CommandManager.literal("weather")
						.then(CommandManager.argument("type", StringArgumentType.word())
							.suggests((c, b) -> {
								String current = null;
								try {
									MapPreset p = PresetStorage.load(c.getSource().getServer(), StringArgumentType.getString(c, "name"));
									if (p != null && p.weather != null) current = p.weather.name().toLowerCase(java.util.Locale.ROOT);
								} catch (IOException ignored) {}
								if (current != null) b.suggest(current);
								for (WeatherType w : WeatherType.values()) {
									String v = w.name().toLowerCase(java.util.Locale.ROOT);
									if (!v.equals(current)) b.suggest(v);
								}
								return b.buildFuture();
							})
							.executes(ctx -> runEditWeather(ctx,
								StringArgumentType.getString(ctx, "name"),
								StringArgumentType.getString(ctx, "type")))))
					.then(CommandManager.literal("fogcolor")
						.then(CommandManager.argument("hex", StringArgumentType.word())
							.suggests(suggestFromPreset(p -> {
								if (p.fogColor == null) return "clear";
								return String.format(java.util.Locale.ROOT, "#%06X", p.fogColor & 0xFFFFFF);
							}))
							.executes(ctx -> runEditFogColor(ctx,
								StringArgumentType.getString(ctx, "name"),
								StringArgumentType.getString(ctx, "hex")))))
					.then(CommandManager.literal("wholemap")
						.then(intArg("minX", p -> p.wholeMapArea == null ? null : fmtNum(p.wholeMapArea.minX),
							intArg("minY", p -> p.wholeMapArea == null ? null : fmtNum(p.wholeMapArea.minY),
								intArg("minZ", p -> p.wholeMapArea == null ? null : fmtNum(p.wholeMapArea.minZ),
									intArg("maxX", p -> p.wholeMapArea == null ? null : fmtNum(p.wholeMapArea.maxX),
										intArg("maxY", p -> p.wholeMapArea == null ? null : fmtNum(p.wholeMapArea.maxY),
											intArgExec("maxZ", p -> p.wholeMapArea == null ? null : fmtNum(p.wholeMapArea.maxZ),
												ctx -> runEditBox(ctx, BoxKind.WHOLE_MAP,
													IntegerArgumentType.getInteger(ctx, "minX"),
													IntegerArgumentType.getInteger(ctx, "minY"),
													IntegerArgumentType.getInteger(ctx, "minZ"),
													IntegerArgumentType.getInteger(ctx, "maxX"),
													IntegerArgumentType.getInteger(ctx, "maxY"),
													IntegerArgumentType.getInteger(ctx, "maxZ"))))))))))
					.then(CommandManager.literal("playarea")
						.then(intArg("minX", p -> p.playArea == null ? null : fmtNum(p.playArea.minX),
							intArg("minY", p -> p.playArea == null ? null : fmtNum(p.playArea.minY),
								intArg("minZ", p -> p.playArea == null ? null : fmtNum(p.playArea.minZ),
									intArg("maxX", p -> p.playArea == null ? null : fmtNum(p.playArea.maxX),
										intArg("maxY", p -> p.playArea == null ? null : fmtNum(p.playArea.maxY),
											intArgExec("maxZ", p -> p.playArea == null ? null : fmtNum(p.playArea.maxZ),
												ctx -> runEditBox(ctx, BoxKind.PLAY_AREA,
													IntegerArgumentType.getInteger(ctx, "minX"),
													IntegerArgumentType.getInteger(ctx, "minY"),
													IntegerArgumentType.getInteger(ctx, "minZ"),
													IntegerArgumentType.getInteger(ctx, "maxX"),
													IntegerArgumentType.getInteger(ctx, "maxY"),
													IntegerArgumentType.getInteger(ctx, "maxZ"))))))))))
					.then(CommandManager.literal("template")
						.then(intArg("minX", p -> p.resetTemplateArea == null ? null : fmtNum(p.resetTemplateArea.minX),
							intArg("minY", p -> p.resetTemplateArea == null ? null : fmtNum(p.resetTemplateArea.minY),
								intArg("minZ", p -> p.resetTemplateArea == null ? null : fmtNum(p.resetTemplateArea.minZ),
									intArg("maxX", p -> p.resetTemplateArea == null ? null : fmtNum(p.resetTemplateArea.maxX),
										intArg("maxY", p -> p.resetTemplateArea == null ? null : fmtNum(p.resetTemplateArea.maxY),
											intArgExec("maxZ", p -> p.resetTemplateArea == null ? null : fmtNum(p.resetTemplateArea.maxZ),
												ctx -> runEditBox(ctx, BoxKind.TEMPLATE,
													IntegerArgumentType.getInteger(ctx, "minX"),
													IntegerArgumentType.getInteger(ctx, "minY"),
													IntegerArgumentType.getInteger(ctx, "minZ"),
													IntegerArgumentType.getInteger(ctx, "maxX"),
													IntegerArgumentType.getInteger(ctx, "maxY"),
													IntegerArgumentType.getInteger(ctx, "maxZ"))))))))))
					.then(CommandManager.literal("offset")
						.then(intArg("x", p -> p.playAreaOffset == null ? null : Integer.toString(p.playAreaOffset.x),
							intArg("y", p -> p.playAreaOffset == null ? null : Integer.toString(p.playAreaOffset.y),
								intArgExec("z", p -> p.playAreaOffset == null ? null : Integer.toString(p.playAreaOffset.z),
									ctx -> runEditOffset(ctx,
										IntegerArgumentType.getInteger(ctx, "x"),
										IntegerArgumentType.getInteger(ctx, "y"),
										IntegerArgumentType.getInteger(ctx, "z")))))))
					.then(CommandManager.literal("spectator")
						.then(doubleArg("x", p -> p.spectatorSpawnPos == null ? null : fmtNum(p.spectatorSpawnPos.x),
							doubleArg("y", p -> p.spectatorSpawnPos == null ? null : fmtNum(p.spectatorSpawnPos.y),
								doubleArg("z", p -> p.spectatorSpawnPos == null ? null : fmtNum(p.spectatorSpawnPos.z),
									floatArg("yaw", p -> p.spectatorSpawnPos == null ? null : fmtNum(p.spectatorSpawnPos.yaw),
										floatArgExec("pitch", p -> p.spectatorSpawnPos == null ? null : fmtNum(p.spectatorSpawnPos.pitch),
											ctx -> runEditSpectator(ctx,
												DoubleArgumentType.getDouble(ctx, "x"),
												DoubleArgumentType.getDouble(ctx, "y"),
												DoubleArgumentType.getDouble(ctx, "z"),
												FloatArgumentType.getFloat(ctx, "yaw"),
												FloatArgumentType.getFloat(ctx, "pitch")))))))))
					.then(CommandManager.literal("readytrain")
						.then(CommandManager.literal("here")
							.executes(MapSelectCommand::runEditReadyTrainHere))
						.then(CommandManager.literal("clear")
							.executes(MapSelectCommand::runEditReadyTrainClear))
						.then(CommandManager.argument("corner", BlockPosArgumentType.blockPos())
							.executes(ctx -> runEditReadyTrainCorner(ctx,
								BlockPosArgumentType.getBlockPos(ctx, "corner")))))
					.then(CommandManager.literal("randomspawns")
						.then(CommandManager.literal("snapshot")
							.executes(MapSelectCommand::runEditRandomSpawnsSnapshot))
						.then(CommandManager.literal("clear")
							.executes(MapSelectCommand::runEditRandomSpawnsClear)))))
			.then(CommandManager.literal("list")
				.requires(OP_OR_HOST)
				.executes(MapSelectCommand::runList))
			.then(CommandManager.literal("show")
				.requires(OP)
				.then(CommandManager.argument("name", StringArgumentType.word())
					.suggests(nameSuggestions)
					.executes(ctx -> runShow(ctx, StringArgumentType.getString(ctx, "name")))))
			.then(CommandManager.literal("set")
				.requires(OP_OR_HOST)
				.then(CommandManager.argument("name", StringArgumentType.word())
					.suggests(nameSuggestions)
					.executes(ctx -> runSet(ctx, StringArgumentType.getString(ctx, "name"), null))
					.then(CommandManager.argument("trainPreset", StringArgumentType.word())
						.suggests(TrainCommand::suggestTrainNames)
						.executes(ctx -> runSet(ctx,
							StringArgumentType.getString(ctx, "name"),
							StringArgumentType.getString(ctx, "trainPreset"))))))
			.then(CommandManager.literal("default")
				.requires(OP)
				.then(CommandManager.argument("name", StringArgumentType.word())
					.suggests(nameSuggestions)
					.then(CommandManager.argument("trainPreset", StringArgumentType.word())
						.suggests(TrainCommand::suggestTrainNames)
						.executes(ctx -> runSetDefaultTrain(ctx,
							StringArgumentType.getString(ctx, "name"),
							StringArgumentType.getString(ctx, "trainPreset"))))))
			.then(CommandManager.literal("snapshot")
				.requires(OP)
				.then(CommandManager.argument("name", StringArgumentType.word())
					.suggests(nameSuggestions)
					.executes(ctx -> runSnapshot(ctx, StringArgumentType.getString(ctx, "name")))));
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Integer> intArg(
			String name, java.util.function.Function<MapPreset, String> extractor,
			com.mojang.brigadier.builder.ArgumentBuilder<ServerCommandSource, ?> next) {
		return CommandManager.argument(name, IntegerArgumentType.integer())
			.suggests(suggestFromPreset(extractor))
			.then(next);
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Integer> intArgExec(
			String name, java.util.function.Function<MapPreset, String> extractor,
			Command<ServerCommandSource> exec) {
		return CommandManager.argument(name, IntegerArgumentType.integer())
			.suggests(suggestFromPreset(extractor))
			.executes(exec);
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Double> doubleArg(
			String name, java.util.function.Function<MapPreset, String> extractor,
			com.mojang.brigadier.builder.ArgumentBuilder<ServerCommandSource, ?> next) {
		return CommandManager.argument(name, DoubleArgumentType.doubleArg())
			.suggests(suggestFromPreset(extractor))
			.then(next);
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Float> floatArg(
			String name, java.util.function.Function<MapPreset, String> extractor,
			com.mojang.brigadier.builder.ArgumentBuilder<ServerCommandSource, ?> next) {
		return CommandManager.argument(name, FloatArgumentType.floatArg())
			.suggests(suggestFromPreset(extractor))
			.then(next);
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Float> floatArgExec(
			String name, java.util.function.Function<MapPreset, String> extractor,
			Command<ServerCommandSource> exec) {
		return CommandManager.argument(name, FloatArgumentType.floatArg())
			.suggests(suggestFromPreset(extractor))
			.executes(exec);
	}

	private static SuggestionProvider<ServerCommandSource> suggestFromPreset(
			java.util.function.Function<MapPreset, String> extractor) {
		return (ctx, b) -> {
			String name = StringArgumentType.getString(ctx, "name");
			if (!PresetStorage.isValidName(name)) return b.buildFuture();
			try {
				MapPreset p = PresetStorage.load(ctx.getSource().getServer(), name);
				if (p != null) {
					String v = extractor.apply(p);
					if (v != null && !v.isEmpty()) b.suggest(v);
				}
			} catch (IOException ignored) {
			}
			return b.buildFuture();
		};
	}

	private static String fmtNum(double d) {
		if (d == Math.floor(d) && !Double.isInfinite(d)) return Long.toString((long) d);
		return String.valueOf(d);
	}

	private static String fmtNum(float f) {
		if (f == Math.floor(f) && !Float.isInfinite(f)) return Integer.toString((int) f);
		return String.valueOf(f);
	}

	private static CompletableFuture<Suggestions> suggestPresetNames(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
		try {
			List<String> names = PresetStorage.list(ctx.getSource().getServer());
			String remaining = builder.getRemainingLowerCase();
			for (String n : names) {
				if (n.toLowerCase().startsWith(remaining)) {
					builder.suggest(n);
				}
			}
		} catch (IOException ignored) {
		}
		return builder.buildFuture();
	}

	private static final double Z_STEP_AREA = 730.0;
	private static final double Z_STEP_OFFSET = 720.0;

	// Validates the name and loads the preset. On failure, sends an error to src and returns null.
	// Returns the loaded preset on success (never null).
	private static MapPreset loadOrError(ServerCommandSource src, String name) throws IOException {
		if (!PresetStorage.isValidName(name)) {
			src.sendError(Text.literal("Invalid preset name."));
			return null;
		}
		MapPreset p = PresetStorage.load(src.getServer(), name);
		if (p == null) {
			src.sendError(Text.literal("Preset '" + name + "' does not exist."));
		}
		return p;
	}

	private static void saveAndBroadcast(ServerCommandSource src, String name, MapPreset preset) throws IOException {
		PresetStorage.save(src.getServer(), name, preset);
		GexpressPresetsSyncHandler.broadcastPresets(src.getServer());
	}

	private static int runCreate(CommandContext<ServerCommandSource> ctx, BlockPos c1, BlockPos c2, String name) {
		ServerCommandSource src = ctx.getSource();
		if (!PresetStorage.isValidName(name)) {
			src.sendError(Text.literal("Invalid preset name. Use letters, digits, '_' or '-', up to 64 chars."));
			return 0;
		}
		try {
			if (PresetStorage.exists(src.getServer(), name)) {
				src.sendError(Text.literal("Preset '" + name + "' already exists. Use /g setup map edit or /g setup map delete."));
				return 0;
			}

			MapPreset.BoxData newWholeMap = MapPreset.BoxData.fromCorners(toVec(c1), toVec(c2));
			double newCenterZ = (newWholeMap.minZ + newWholeMap.maxZ) / 2.0;

			Neighbor neighbor = findNearestNeighbor(src, newCenterZ, null);

			MapPreset preset;
			String feedbackSuffix;
			if (neighbor != null) {
				int direction = newCenterZ >= neighbor.centerZ ? 1 : -1;
				preset = derivedFromNeighbor(neighbor.preset, direction);
				preset.wholeMapArea = newWholeMap;
				String sign = direction > 0 ? "+" : "-";
				feedbackSuffix = " — derived from '" + neighbor.name + "' (z shift " + sign + (int) Z_STEP_AREA + " / offset " + sign + (int) Z_STEP_OFFSET + ").";
			} else {
				preset = MapPreset.from(src.getWorld());
				preset.wholeMapArea = newWholeMap;
				feedbackSuffix = " — no existing presets, snapshotted current WATHE values and RTP slots.";
			}

			saveAndBroadcast(src, name, preset);
			final String suffix = feedbackSuffix;
			src.sendFeedback(() -> Text.literal("Created '" + name + "'" + suffix).formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to create preset: " + e.getMessage()));
			return 0;
		}
	}

	private record Neighbor(String name, MapPreset preset, double centerZ) {}

	private static Neighbor findNearestNeighbor(ServerCommandSource src, double targetZ, String excludeName) throws IOException {
		String bestName = null;
		MapPreset bestPreset = null;
		double bestZ = 0;
		double minDist = Double.POSITIVE_INFINITY;
		for (String n : PresetStorage.list(src.getServer())) {
			if (n.equals(excludeName)) continue;
			MapPreset p = PresetStorage.load(src.getServer(), n);
			if (p == null) continue;
			Double cz = null;
			if (p.wholeMapArea != null) cz = (p.wholeMapArea.minZ + p.wholeMapArea.maxZ) / 2.0;
			else if (p.playArea != null) cz = (p.playArea.minZ + p.playArea.maxZ) / 2.0;
			if (cz == null) continue;
			double dist = Math.abs(cz - targetZ);
			if (dist < minDist) {
				minDist = dist;
				bestPreset = p;
				bestName = n;
				bestZ = cz;
			}
		}
		return bestPreset == null ? null : new Neighbor(bestName, bestPreset, bestZ);
	}

	private static MapPreset derivedFromNeighbor(MapPreset neighbor, int direction) {
		double areaShift = direction * Z_STEP_AREA;
		int offsetShift = direction * (int) Z_STEP_OFFSET;
		MapPreset preset = new MapPreset();
		if (neighbor.playArea != null) {
			MapPreset.BoxData pa = new MapPreset.BoxData();
			pa.minX = neighbor.playArea.minX;
			pa.minY = neighbor.playArea.minY;
			pa.minZ = neighbor.playArea.minZ + areaShift;
			pa.maxX = neighbor.playArea.maxX;
			pa.maxY = neighbor.playArea.maxY;
			pa.maxZ = neighbor.playArea.maxZ + areaShift;
			preset.playArea = pa;
		}
		if (neighbor.readyArea != null) {
			MapPreset.BoxData ra = new MapPreset.BoxData();
			ra.minX = neighbor.readyArea.minX;
			ra.minY = neighbor.readyArea.minY;
			ra.minZ = neighbor.readyArea.minZ + areaShift;
			ra.maxX = neighbor.readyArea.maxX;
			ra.maxY = neighbor.readyArea.maxY;
			ra.maxZ = neighbor.readyArea.maxZ + areaShift;
			preset.readyArea = ra;
		}
		if (neighbor.lobbyArea != null) {
			MapPreset.BoxData la = new MapPreset.BoxData();
			la.minX = neighbor.lobbyArea.minX;
			la.minY = neighbor.lobbyArea.minY;
			la.minZ = neighbor.lobbyArea.minZ + areaShift;
			la.maxX = neighbor.lobbyArea.maxX;
			la.maxY = neighbor.lobbyArea.maxY;
			la.maxZ = neighbor.lobbyArea.maxZ + areaShift;
			preset.lobbyArea = la;
		}
		if (neighbor.spectatorSpawnPos != null) {
			MapPreset.PosData ss = new MapPreset.PosData();
			ss.x = neighbor.spectatorSpawnPos.x;
			ss.y = neighbor.spectatorSpawnPos.y;
			ss.z = neighbor.spectatorSpawnPos.z + areaShift;
			ss.yaw = neighbor.spectatorSpawnPos.yaw;
			ss.pitch = neighbor.spectatorSpawnPos.pitch;
			preset.spectatorSpawnPos = ss;
		}
		if (neighbor.readyAreaSpawnPos != null) {
			MapPreset.PosData rs = new MapPreset.PosData();
			rs.x = neighbor.readyAreaSpawnPos.x;
			rs.y = neighbor.readyAreaSpawnPos.y;
			rs.z = neighbor.readyAreaSpawnPos.z + areaShift;
			rs.yaw = neighbor.readyAreaSpawnPos.yaw;
			rs.pitch = neighbor.readyAreaSpawnPos.pitch;
			preset.readyAreaSpawnPos = rs;
		}
		if (neighbor.playAreaOffset != null) {
			MapPreset.OffsetData o = new MapPreset.OffsetData();
			o.x = neighbor.playAreaOffset.x;
			o.y = neighbor.playAreaOffset.y;
			o.z = neighbor.playAreaOffset.z + offsetShift;
			preset.playAreaOffset = o;
		}
		if (neighbor.resetTemplateArea != null) {
			MapPreset.BoxData ta = new MapPreset.BoxData();
			ta.minX = neighbor.resetTemplateArea.minX;
			ta.minY = neighbor.resetTemplateArea.minY;
			ta.minZ = neighbor.resetTemplateArea.minZ + offsetShift;
			ta.maxX = neighbor.resetTemplateArea.maxX;
			ta.maxY = neighbor.resetTemplateArea.maxY;
			ta.maxZ = neighbor.resetTemplateArea.maxZ + offsetShift;
			preset.resetTemplateArea = ta;
		}
		preset.weather = neighbor.weather == null ? WeatherType.NONE : neighbor.weather;
		preset.fogColor = neighbor.fogColor;
		return preset;
	}

	private static int runDelete(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		if (!PresetStorage.isValidName(name)) {
			src.sendError(Text.literal("Invalid preset name."));
			return 0;
		}
		try {
			boolean deleted = PresetStorage.delete(src.getServer(), name);
			if (!deleted) {
				src.sendError(Text.literal("Preset '" + name + "' does not exist."));
				return 0;
			}
			GexpressPresetsSyncHandler.broadcastPresets(src.getServer());
			src.sendFeedback(() -> Text.literal("Deleted preset '" + name + "'.").formatted(Formatting.YELLOW), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to delete preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runEditCorners(CommandContext<ServerCommandSource> ctx, String name, BlockPos c1, BlockPos c2) {
		ServerCommandSource src = ctx.getSource();
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;

			MapPreset.BoxData newWholeMap = MapPreset.BoxData.fromCorners(toVec(c1), toVec(c2));
			double newCenterZ = (newWholeMap.minZ + newWholeMap.maxZ) / 2.0;

			Neighbor neighbor = findNearestNeighbor(src, newCenterZ, name);

			String feedbackSuffix;
			if (neighbor != null) {
				int direction = newCenterZ >= neighbor.centerZ ? 1 : -1;
				MapPreset derived = derivedFromNeighbor(neighbor.preset, direction);
				preset.playArea = derived.playArea;
				preset.readyArea = derived.readyArea;
				preset.lobbyArea = derived.lobbyArea;
				preset.spectatorSpawnPos = derived.spectatorSpawnPos;
				preset.readyAreaSpawnPos = derived.readyAreaSpawnPos;
				preset.playAreaOffset = derived.playAreaOffset;
				preset.resetTemplateArea = derived.resetTemplateArea;
				preset.weather = derived.weather;
				preset.fogColor = derived.fogColor;
				String sign = direction > 0 ? "+" : "-";
				feedbackSuffix = " — re-derived from '" + neighbor.name + "' (z shift " + sign + (int) Z_STEP_AREA + " / offset " + sign + (int) Z_STEP_OFFSET + ").";
			} else {
				feedbackSuffix = " — no other presets, kept existing values.";
			}
			preset.wholeMapArea = newWholeMap;

			saveAndBroadcast(src, name, preset);
			final String suffix = feedbackSuffix;
			src.sendFeedback(() -> Text.literal("Updated corners for '" + name + "'" + suffix).formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to edit preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runEditRename(CommandContext<ServerCommandSource> ctx, String oldName, String newName) {
		ServerCommandSource src = ctx.getSource();
		if (!PresetStorage.isValidName(oldName) || !PresetStorage.isValidName(newName)) {
			src.sendError(Text.literal("Invalid preset name. Use letters, digits, '_' or '-', up to 64 chars."));
			return 0;
		}
		if (oldName.equals(newName)) {
			src.sendError(Text.literal("New name is the same as the old name."));
			return 0;
		}
		try {
			PresetStorage.rename(src.getServer(), oldName, newName);
			GexpressPresetsSyncHandler.broadcastPresets(src.getServer());
			src.sendFeedback(() -> Text.literal("Renamed '" + oldName + "' to '" + newName + "'.").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal(e.getMessage()));
			return 0;
		}
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		try {
			List<String> names = PresetStorage.list(src.getServer());
			if (names.isEmpty()) {
				src.sendFeedback(() -> Text.literal("No presets saved. Use /g setup map create <corner1> <corner2> <name>."), false);
				return 1;
			}
			String joined = String.join(", ", names);
			src.sendFeedback(() -> Text.literal("Presets (" + names.size() + "): " + joined), false);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to list presets: " + e.getMessage()));
			return 0;
		}
	}

	private static int runShow(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;

			src.sendFeedback(() -> Text.literal("─── Map '" + name + "' ───").formatted(Formatting.GOLD, Formatting.BOLD), false);
			sendBoxLine(src, "Whole Map Area", preset.wholeMapArea);
			sendBoxLine(src, "Lobby Area", preset.lobbyArea);
			sendBoxLine(src, "Ready Area", preset.readyArea);
			sendBoxLine(src, "Play Area", preset.playArea);
			sendBoxLine(src, "Template Area", preset.resetTemplateArea);
			sendOffsetLine(src, "Play Area Offset", preset.playAreaOffset);
			sendPosLine(src, "Ready Area Spawn", preset.readyAreaSpawnPos);
			sendPosLine(src, "Spectator Spawn", preset.spectatorSpawnPos);
			sendWeatherLine(src, "Weather", preset.weather);
			sendFogLine(src, "Fog Color", preset.fogColor);
			sendDefaultTrainLine(src, "Default Train", preset.defaultTrainPreset);
			sendOffsetLine(src, "Ready Train Corner", preset.lobbyTrainCorner);
			sendRandomSpawnsLine(src, "Random Spawn Positions", preset.randomSpawnPositions);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to load preset: " + e.getMessage()));
			return 0;
		}
	}

	private static void sendBoxLine(ServerCommandSource src, String label, MapPreset.BoxData b) {
		Text value;
		if (b == null) {
			value = Text.literal("(not set)").formatted(Formatting.DARK_GRAY);
		} else {
			String size = fmt(b.sizeX()) + " × " + fmt(b.sizeY()) + " × " + fmt(b.sizeZ());
			String coords = "(" + fmt(b.minX) + ", " + fmt(b.minY) + ", " + fmt(b.minZ) + ") → ("
				+ fmt(b.maxX) + ", " + fmt(b.maxY) + ", " + fmt(b.maxZ) + ")";
			value = Text.literal(coords).formatted(Formatting.WHITE)
				.append(Text.literal("  [" + size + "]").formatted(Formatting.DARK_GRAY));
		}
		src.sendFeedback(() -> Text.literal(label + ": ").formatted(Formatting.AQUA).append(value), false);
	}

	private static void sendOffsetLine(ServerCommandSource src, String label, MapPreset.OffsetData o) {
		Text value;
		if (o == null) {
			value = Text.literal("(not set)").formatted(Formatting.DARK_GRAY);
		} else {
			String coords = "(" + o.x + ", " + o.y + ", " + o.z + ")";
			value = Text.literal(coords).formatted(Formatting.WHITE);
		}
		src.sendFeedback(() -> Text.literal(label + ": ").formatted(Formatting.AQUA).append(value), false);
	}

	private static void sendPosLine(ServerCommandSource src, String label, MapPreset.PosData p) {
		Text value;
		if (p == null) {
			value = Text.literal("(not set)").formatted(Formatting.DARK_GRAY);
		} else {
			String coords = "(" + fmt(p.x) + ", " + fmt(p.y) + ", " + fmt(p.z) + ")";
			String facing = "yaw " + fmt(p.yaw) + ", pitch " + fmt(p.pitch);
			value = Text.literal(coords).formatted(Formatting.WHITE)
				.append(Text.literal("  [" + facing + "]").formatted(Formatting.DARK_GRAY));
		}
		src.sendFeedback(() -> Text.literal(label + ": ").formatted(Formatting.AQUA).append(value), false);
	}

	private static Vec3d toVec(BlockPos p) {
		return new Vec3d(p.getX(), p.getY(), p.getZ());
	}

	private static String fmt(double d) {
		if (d == Math.floor(d) && !Double.isInfinite(d)) {
			return Long.toString((long) d);
		}
		return String.format(java.util.Locale.ROOT, "%.1f", d);
	}

	private static void sendWeatherLine(ServerCommandSource src, String label, WeatherType w) {
		String v = (w == null ? WeatherType.NONE : w).name().toLowerCase(java.util.Locale.ROOT);
		src.sendFeedback(() -> Text.literal(label + ": ").formatted(Formatting.AQUA)
			.append(Text.literal(v).formatted(Formatting.WHITE)), false);
	}

	private static void sendDefaultTrainLine(ServerCommandSource src, String label, String trainName) {
		Text value;
		if (trainName == null || trainName.isEmpty()) {
			value = Text.literal("(none)").formatted(Formatting.DARK_GRAY);
		} else {
			value = Text.literal(trainName).formatted(Formatting.WHITE);
		}
		src.sendFeedback(() -> Text.literal(label + ": ").formatted(Formatting.AQUA).append(value), false);
	}

	private static void sendRandomSpawnsLine(ServerCommandSource src, String label, List<MapPreset.PosData> spawns) {
		int count = spawns == null ? 0 : spawns.size();
		Text value = Text.literal(Integer.toString(count)).formatted(count == 0 ? Formatting.DARK_GRAY : Formatting.WHITE);
		src.sendFeedback(() -> Text.literal(label + ": ").formatted(Formatting.AQUA).append(value), false);
	}

	private static void sendFogLine(ServerCommandSource src, String label, Integer fogColor) {
		Text value;
		if (fogColor == null) {
			value = Text.literal("(not set)").formatted(Formatting.DARK_GRAY);
		} else {
			value = Text.literal(String.format(java.util.Locale.ROOT, "#%06X", fogColor & 0xFFFFFF)).formatted(Formatting.WHITE);
		}
		src.sendFeedback(() -> Text.literal(label + ": ").formatted(Formatting.AQUA).append(value), false);
	}

	private static Integer parseHexColor(String s) {
		if (s == null) return null;
		String c = s.startsWith("#") ? s.substring(1) : (s.startsWith("0x") || s.startsWith("0X") ? s.substring(2) : s);
		if (c.length() != 6) return null;
		try {
			return Integer.parseInt(c, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int runEditWeather(CommandContext<ServerCommandSource> ctx, String name, String type) {
		ServerCommandSource src = ctx.getSource();
		WeatherType w = parseWeatherType(type);
		if (w == null) {
			src.sendError(Text.literal("Invalid weather type '" + type + "'. Use none, snow, or sandstorm."));
			return 0;
		}
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;
			preset.weather = w;
			saveAndBroadcast(src, name, preset);
			src.sendFeedback(() -> Text.literal("Set weather for '" + name + "' to " + w.name().toLowerCase(java.util.Locale.ROOT) + ".").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to edit preset: " + e.getMessage()));
			return 0;
		}
	}

	private static WeatherType parseWeatherType(String type) {
		if (type == null) return null;
		for (WeatherType weather : WeatherType.values()) {
			if (weather.name().equalsIgnoreCase(type)) return weather;
		}
		return null;
	}

	private static int runEditFogColor(CommandContext<ServerCommandSource> ctx, String name, String hex) {
		ServerCommandSource src = ctx.getSource();
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;
			if ("clear".equalsIgnoreCase(hex) || "none".equalsIgnoreCase(hex)) {
				preset.fogColor = null;
				saveAndBroadcast(src, name, preset);
				src.sendFeedback(() -> Text.literal("Cleared fog color override for '" + name + "'.").formatted(Formatting.GREEN), true);
				return 1;
			}
			Integer color = parseHexColor(hex);
			if (color == null) {
				src.sendError(Text.literal("Invalid hex color '" + hex + "'. Use #RRGGBB or 'clear'."));
				return 0;
			}
			preset.fogColor = color;
			saveAndBroadcast(src, name, preset);
			final String shown = String.format(java.util.Locale.ROOT, "#%06X", color);
			src.sendFeedback(() -> Text.literal("Set fog color for '" + name + "' to " + shown + ".").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to edit preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runSet(CommandContext<ServerCommandSource> ctx, String name, String trainPresetOverride) {
		ServerCommandSource src = ctx.getSource();
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;
			String trainToApply = trainPresetOverride != null ? trainPresetOverride : preset.defaultTrainPreset;
			boolean explicitOverride = trainPresetOverride != null;
			String trainSuffix = "";
			TrainPreset train = null;
			if (trainToApply != null) {
				if (!TrainPresetStorage.isValidName(trainToApply)) {
					if (explicitOverride) {
						src.sendError(Text.literal("Invalid train preset name '" + trainToApply + "'."));
						return 0;
					}
					trainToApply = null;
				} else {
					train = TrainPresetStorage.load(src.getServer(), trainToApply);
					if (train == null) {
						if (explicitOverride) {
							src.sendError(Text.literal("Train preset '" + trainToApply + "' does not exist."));
							return 0;
						}
						// Default train preset was deleted — warn, skip lobby update, but continue map switch.
						final String missing = trainToApply;
						src.sendFeedback(() -> Text.literal("Default train '" + missing + "' no longer exists — lobby not updated.").formatted(Formatting.YELLOW), false);
						trainToApply = null;
					} else {
						train.applyTo(src.getWorld());
						trainSuffix = " with train '" + trainToApply + "'";
					}
				}
			}

			preset.applyTo(src.getWorld());
			boolean appliedRandomSpawns = preset.applyRandomSpawnsTo(src.getWorld());
			MapWeatherComponent weather = MapWeatherComponent.KEY.get(src.getWorld());
			WeatherType w = preset.weather != null ? preset.weather : WeatherType.NONE;
			int fog = preset.fogColor == null ? MapWeatherComponent.NO_FOG_OVERRIDE : preset.fogColor;
			weather.set(w, fog);
			weather.setCurrentMapName(name);

			String previewSuffix = "";
			if (preset.lobbyTrainCorner != null && train != null && train.resetTemplateArea != null) {
				net.minecraft.util.math.Box trainBox = train.resetTemplateArea.toBox();
				TrainPreview.Result r = TrainPreview.apply(src.getWorld(), trainBox,
					preset.lobbyTrainCorner.x, preset.lobbyTrainCorner.y, preset.lobbyTrainCorner.z);
				switch (r) {
					case OK -> previewSuffix = " (ready-area preview updated)";
					case NO_TEMPLATE -> previewSuffix = " (preview skipped: no template area set)";
					case PASTE_FAILED -> previewSuffix = " (preview failed: region not loaded)";
				}
			}

			final String ts = trainSuffix;
			final String ps = previewSuffix;
			final String rs = appliedRandomSpawns ? " (map RTP slots applied)" : "";
			src.sendFeedback(() -> Text.literal("Set active map to '" + name + "'" + ts + ps + rs + ".").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to set preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runEditReadyTrainHere(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (src.getPlayer() == null) {
			src.sendError(Text.literal("'here' requires a player."));
			return 0;
		}
		BlockPos pos = src.getPlayer().getBlockPos();
		return setReadyTrainCorner(ctx, pos);
	}

	private static int runEditReadyTrainCorner(CommandContext<ServerCommandSource> ctx, BlockPos corner) {
		return setReadyTrainCorner(ctx, corner);
	}

	private static int setReadyTrainCorner(CommandContext<ServerCommandSource> ctx, BlockPos corner) {
		ServerCommandSource src = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;
			MapPreset.OffsetData o = new MapPreset.OffsetData();
			o.x = corner.getX();
			o.y = corner.getY();
			o.z = corner.getZ();
			preset.lobbyTrainCorner = o;
			saveAndBroadcast(src, name, preset);
			src.sendFeedback(() -> Text.literal("Ready-area train corner for '" + name + "' set to ("
				+ corner.getX() + ", " + corner.getY() + ", " + corner.getZ() + ").").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to edit preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runEditReadyTrainClear(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;
			preset.lobbyTrainCorner = null;
			saveAndBroadcast(src, name, preset);
			src.sendFeedback(() -> Text.literal("Cleared ready-area train preview for '" + name + "'.").formatted(Formatting.YELLOW), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to edit preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runEditRandomSpawnsSnapshot(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;
			preset.randomSpawnPositions = MapPreset.randomSpawnsFrom(src.getWorld());
			saveAndBroadcast(src, name, preset);
			int count = preset.randomSpawnPositions == null ? 0 : preset.randomSpawnPositions.size();
			src.sendFeedback(() -> Text.literal("Imported " + count + " active RTP slot(s) into '" + name + "'.").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to import RTP slots: " + e.getMessage()));
			return 0;
		}
	}

	private static int runEditRandomSpawnsClear(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;
			int count = preset.randomSpawnPositions == null ? 0 : preset.randomSpawnPositions.size();
			preset.randomSpawnPositions = new java.util.ArrayList<>();
			saveAndBroadcast(src, name, preset);
			src.sendFeedback(() -> Text.literal("Cleared " + count + " random spawn position(s) from '" + name + "'.").formatted(Formatting.YELLOW), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to clear random spawns: " + e.getMessage()));
			return 0;
		}
	}

	private static int runSetDefaultTrain(CommandContext<ServerCommandSource> ctx, String mapName, String trainName) {
		ServerCommandSource src = ctx.getSource();
		if (!TrainPresetStorage.isValidName(trainName)) {
			src.sendError(Text.literal("Invalid train preset name."));
			return 0;
		}
		try {
			MapPreset preset = loadOrError(src, mapName);
			if (preset == null) return 0;
			if (!TrainPresetStorage.exists(src.getServer(), trainName)) {
				src.sendError(Text.literal("Train preset '" + trainName + "' does not exist."));
				return 0;
			}
			preset.defaultTrainPreset = trainName;
			saveAndBroadcast(src, mapName, preset);
			src.sendFeedback(() -> Text.literal("Default train for '" + mapName + "' set to '" + trainName + "'.").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to set default train: " + e.getMessage()));
			return 0;
		}
	}

	private static int runSnapshot(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;
			MapPreset snap = MapPreset.from(src.getWorld());
			preset.playArea = snap.playArea;
			preset.readyArea = snap.readyArea;
			preset.lobbyArea = snap.lobbyArea;
			preset.spectatorSpawnPos = snap.spectatorSpawnPos;
			preset.readyAreaSpawnPos = snap.readyAreaSpawnPos;
			preset.playAreaOffset = snap.playAreaOffset;
			preset.resetTemplateArea = snap.resetTemplateArea;
			preset.randomSpawnPositions = snap.randomSpawnPositions;
			saveAndBroadcast(src, name, preset);
			int count = preset.randomSpawnPositions == null ? 0 : preset.randomSpawnPositions.size();
			src.sendFeedback(() -> Text.literal("Snapshotted current Wathe values and " + count + " RTP slot(s) into '" + name + "'.").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to snapshot preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runEditBox(CommandContext<ServerCommandSource> ctx, BoxKind kind,
			int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		ServerCommandSource src = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;
			MapPreset.BoxData box = new MapPreset.BoxData();
			box.minX = Math.min(minX, maxX);
			box.maxX = Math.max(minX, maxX);
			box.minY = Math.min(minY, maxY);
			box.maxY = Math.max(minY, maxY);
			box.minZ = Math.min(minZ, maxZ);
			box.maxZ = Math.max(minZ, maxZ);
			switch (kind) {
				case WHOLE_MAP -> preset.wholeMapArea = box;
				case PLAY_AREA -> preset.playArea = box;
				case TEMPLATE -> preset.resetTemplateArea = box;
			}
			saveAndBroadcast(src, name, preset);
			src.sendFeedback(() -> Text.literal("Updated " + kind.label + " for '" + name + "'.").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to edit preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runEditOffset(CommandContext<ServerCommandSource> ctx, int x, int y, int z) {
		ServerCommandSource src = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;
			MapPreset.OffsetData o = new MapPreset.OffsetData();
			o.x = x; o.y = y; o.z = z;
			preset.playAreaOffset = o;
			saveAndBroadcast(src, name, preset);
			src.sendFeedback(() -> Text.literal("Updated play area offset for '" + name + "'.").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to edit preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runEditSpectator(CommandContext<ServerCommandSource> ctx,
			double x, double y, double z, float yaw, float pitch) {
		ServerCommandSource src = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		try {
			MapPreset preset = loadOrError(src, name);
			if (preset == null) return 0;
			MapPreset.PosData p = new MapPreset.PosData();
			p.x = x; p.y = y; p.z = z; p.yaw = yaw; p.pitch = pitch;
			preset.spectatorSpawnPos = p;
			saveAndBroadcast(src, name, preset);
			src.sendFeedback(() -> Text.literal("Updated spectator spawn for '" + name + "'.").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to edit preset: " + e.getMessage()));
			return 0;
		}
	}

}
