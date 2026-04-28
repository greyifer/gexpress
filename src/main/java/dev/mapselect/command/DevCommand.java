package dev.mapselect.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.GexpressConfigSyncHandler;
import dev.mapselect.network.GexpressPresetsSyncHandler;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.train.TrainPreset;
import dev.mapselect.preset.train.TrainPresetStorage;
import dev.mapselect.role.bombspecialist.C4PlacementPreset;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class DevCommand {
	private static final Predicate<ServerCommandSource> DEV = GexpressPermissions::canUseAdminCommands;
	private static final List<String> ROLE_PATHS = List.of("bomb_specialist", "medic", "snitch", "seer",
		"time_master", "the_silent", "warlock", "juggernaut", "trickster", "puppetmaster", "pelican");

	private DevCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		SuggestionProvider<ServerCommandSource> rolePaths = DevCommand::suggestRolePaths;
		SuggestionProvider<ServerCommandSource> trainNames = TrainCommand::suggestTrainNames;

		return CommandManager.literal("dev")
			.requires(DEV)
			.then(CommandManager.literal("c4back")
				.then(CommandManager.literal("offset")
					.then(floatSetting("x", GexpressConfig.C4_BACK_OFFSET_MIN, GexpressConfig.C4_BACK_OFFSET_MAX,
						v -> GexpressConfig.c4BackOffsetX = v, GexpressConfig::getC4BackOffsetX))
					.then(floatSetting("y", GexpressConfig.C4_BACK_OFFSET_MIN, GexpressConfig.C4_BACK_OFFSET_MAX,
						v -> GexpressConfig.c4BackOffsetY = v, GexpressConfig::getC4BackOffsetY))
					.then(floatSetting("z", GexpressConfig.C4_BACK_OFFSET_MIN, GexpressConfig.C4_BACK_OFFSET_MAX,
						v -> GexpressConfig.c4BackOffsetZ = v, GexpressConfig::getC4BackOffsetZ)))
				.then(CommandManager.literal("rotation")
					.then(floatSetting("x", GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX,
						v -> GexpressConfig.c4BackRotationX = v, GexpressConfig::getC4BackRotationX))
					.then(floatSetting("y", GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX,
						v -> GexpressConfig.c4BackRotationY = v, GexpressConfig::getC4BackRotationY))
					.then(floatSetting("z", GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX,
						v -> GexpressConfig.c4BackRotationZ = v, GexpressConfig::getC4BackRotationZ)))
				.then(floatSetting("slant", GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX,
					v -> GexpressConfig.c4BackSlant = v, GexpressConfig::getC4BackSlant))
				.then(floatSetting("scale", GexpressConfig.C4_BACK_SCALE_MIN, GexpressConfig.C4_BACK_SCALE_MAX,
					v -> GexpressConfig.c4BackScale = v, GexpressConfig::getC4BackScale)))
			.then(CommandManager.literal("c4preset")
				.then(CommandManager.literal("add")
					.executes(ctx -> runC4PresetAdd(ctx, GexpressConfig.getCurrentC4PlacementPresetString()))
					.then(CommandManager.argument("values", StringArgumentType.greedyString())
						.executes(ctx -> runC4PresetAdd(ctx, StringArgumentType.getString(ctx, "values")))))
				.then(CommandManager.literal("list").executes(DevCommand::runC4PresetList))
				.then(CommandManager.literal("remove")
					.then(CommandManager.argument("index", IntegerArgumentType.integer(1))
						.executes(ctx -> runC4PresetRemove(ctx, IntegerArgumentType.getInteger(ctx, "index")))))
				.then(CommandManager.literal("clear").executes(DevCommand::runC4PresetClear)))
			.then(CommandManager.literal("roledesc")
				.then(CommandManager.argument("role", StringArgumentType.word())
					.suggests(rolePaths)
					.then(CommandManager.literal("set")
						.then(CommandManager.argument("description", StringArgumentType.greedyString())
							.executes(ctx -> runRoleDescSet(ctx,
								StringArgumentType.getString(ctx, "role"),
								StringArgumentType.getString(ctx, "description")))))
					.then(CommandManager.literal("clear")
						.executes(ctx -> runRoleDescSet(ctx, StringArgumentType.getString(ctx, "role"), "")))))
			.then(CommandManager.literal("shortsighted")
				.then(CommandManager.literal("range")
					.then(CommandManager.argument("value", FloatArgumentType.floatArg(
							GexpressConfig.SHORT_SIGHTED_ENTITY_RANGE_MIN,
							GexpressConfig.SHORT_SIGHTED_ENTITY_RANGE_MAX))
						.executes(ctx -> runFloatSet(ctx, "shortsighted range",
							FloatArgumentType.getFloat(ctx, "value"),
							v -> GexpressConfig.shortSightedFogRange = v,
							GexpressConfig::getShortSightedEntityRange)))))
			.then(CommandManager.literal("medicshield")
				.then(intSetting("blockFlashTicks", GexpressConfig.MEDIC_SHIELD_FLASH_TICKS_MIN,
					GexpressConfig.MEDIC_SHIELD_FLASH_TICKS_MAX,
					v -> GexpressConfig.medicShieldBlockFlashTicks = v,
					GexpressConfig::getMedicShieldBlockFlashTicks))
				.then(intSetting("breakFlashTicks", GexpressConfig.MEDIC_SHIELD_FLASH_TICKS_MIN,
					GexpressConfig.MEDIC_SHIELD_FLASH_TICKS_MAX,
					v -> GexpressConfig.medicShieldBreakFlashTicks = v,
					GexpressConfig::getMedicShieldBreakFlashTicks))
				.then(intSetting("blockFlashAlpha", GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MIN,
					GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MAX,
					v -> GexpressConfig.medicShieldBlockFlashAlpha = v,
					GexpressConfig::getMedicShieldBlockFlashAlpha))
				.then(intSetting("breakFlashAlpha", GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MIN,
					GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MAX,
					v -> GexpressConfig.medicShieldBreakFlashAlpha = v,
					GexpressConfig::getMedicShieldBreakFlashAlpha)))
			.then(CommandManager.literal("silentshadow")
				.then(CommandManager.literal("alpha")
					.then(CommandManager.argument("value", FloatArgumentType.floatArg(
							GexpressConfig.SILENT_SHADOW_ALPHA_MIN,
							GexpressConfig.SILENT_SHADOW_ALPHA_MAX))
						.executes(ctx -> runFloatSet(ctx, "silent shadow alpha",
							FloatArgumentType.getFloat(ctx, "value"),
							v -> GexpressConfig.silentShadowAlpha = v,
							GexpressConfig::getSilentShadowAlpha)))))
			.then(CommandManager.literal("traincart")
				.then(CommandManager.argument("preset", StringArgumentType.word())
					.suggests(trainNames)
					.then(CommandManager.literal("list")
						.executes(ctx -> runTraincartList(ctx, StringArgumentType.getString(ctx, "preset"))))
					.then(CommandManager.literal("remove")
						.then(CommandManager.argument("index", IntegerArgumentType.integer(1))
							.executes(ctx -> runTraincartRemove(ctx,
								StringArgumentType.getString(ctx, "preset"),
								IntegerArgumentType.getInteger(ctx, "index")))))
					.then(CommandManager.literal("clear")
						.executes(ctx -> runTraincartClear(ctx, StringArgumentType.getString(ctx, "preset"))))
					.then(CommandManager.argument("corner1", BlockPosArgumentType.blockPos())
						.then(CommandManager.argument("corner2", BlockPosArgumentType.blockPos())
							.executes(ctx -> runTraincartAdd(ctx,
								StringArgumentType.getString(ctx, "preset"),
								BlockPosArgumentType.getBlockPos(ctx, "corner1"),
								BlockPosArgumentType.getBlockPos(ctx, "corner2")))))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> floatSetting(String name, float min, float max,
			Consumer<Float> setter, Supplier<Float> getter) {
		return CommandManager.literal(name)
			.then(CommandManager.argument("value", FloatArgumentType.floatArg(min, max))
				.executes(ctx -> runFloatSet(ctx, name, FloatArgumentType.getFloat(ctx, "value"), setter, getter)));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> intSetting(String name, int min, int max,
			IntConsumer setter, IntSupplier getter) {
		return CommandManager.literal(name)
			.then(CommandManager.argument("value", IntegerArgumentType.integer(min, max))
				.executes(ctx -> runIntSet(ctx, name, IntegerArgumentType.getInteger(ctx, "value"), setter, getter)));
	}

	private static int runFloatSet(CommandContext<ServerCommandSource> ctx, String label, float value,
			Consumer<Float> setter, Supplier<Float> getter) {
		setter.accept(value);
		syncConfig(ctx.getSource());
		ctx.getSource().sendFeedback(() -> Text.literal("Set " + label + " to " + format(getter.get()) + ".")
			.formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int runIntSet(CommandContext<ServerCommandSource> ctx, String label, int value,
			IntConsumer setter, IntSupplier getter) {
		setter.accept(value);
		syncConfig(ctx.getSource());
		ctx.getSource().sendFeedback(() -> Text.literal("Set " + label + " to " + getter.getAsInt() + ".")
			.formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int runC4PresetAdd(CommandContext<ServerCommandSource> ctx, String raw) {
		C4PlacementPreset preset = C4PlacementPreset.parse(raw);
		if (preset == null) {
			ctx.getSource().sendError(Text.literal("C4 preset must be: x y z rotX rotY rotZ slant scale"));
			return 0;
		}
		List<String> presets = new ArrayList<>(GexpressConfig.getC4PlacementPresetStrings());
		presets.add(preset.toConfigString());
		GexpressConfig.setC4PlacementPresetStrings(presets);
		syncConfig(ctx.getSource());
		int index = GexpressConfig.getC4PlacementPresetStrings().size();
		ctx.getSource().sendFeedback(() -> Text.literal("Added C4 placement preset #" + index + ".")
			.formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int runC4PresetList(CommandContext<ServerCommandSource> ctx) {
		List<String> presets = GexpressConfig.getC4PlacementPresetStrings();
		if (presets.isEmpty()) {
			ctx.getSource().sendFeedback(() -> Text.literal("No C4 placement presets."), false);
			return 1;
		}
		for (int i = 0; i < presets.size(); i++) {
			final int index = i + 1;
			final String value = presets.get(i);
			ctx.getSource().sendFeedback(() -> Text.literal("#" + index + " " + value), false);
		}
		return presets.size();
	}

	private static int runC4PresetRemove(CommandContext<ServerCommandSource> ctx, int oneBasedIndex) {
		List<String> presets = new ArrayList<>(GexpressConfig.getC4PlacementPresetStrings());
		if (oneBasedIndex < 1 || oneBasedIndex > presets.size()) {
			ctx.getSource().sendError(Text.literal("No C4 placement preset #" + oneBasedIndex + "."));
			return 0;
		}
		presets.remove(oneBasedIndex - 1);
		GexpressConfig.setC4PlacementPresetStrings(presets);
		syncConfig(ctx.getSource());
		ctx.getSource().sendFeedback(() -> Text.literal("Removed C4 placement preset #" + oneBasedIndex + ".")
			.formatted(Formatting.YELLOW), true);
		return 1;
	}

	private static int runC4PresetClear(CommandContext<ServerCommandSource> ctx) {
		GexpressConfig.setC4PlacementPresetStrings(List.of());
		syncConfig(ctx.getSource());
		ctx.getSource().sendFeedback(() -> Text.literal("Cleared C4 placement presets.")
			.formatted(Formatting.YELLOW), true);
		return 1;
	}

	private static int runRoleDescSet(CommandContext<ServerCommandSource> ctx, String rolePath, String description) {
		if (!ROLE_PATHS.contains(rolePath)) {
			ctx.getSource().sendError(Text.literal("Unknown role path: " + rolePath));
			return 0;
		}
		GexpressConfig.setRoleDescriptionOverride(rolePath, description);
		syncConfig(ctx.getSource());
		boolean cleared = description == null || description.isBlank();
		ctx.getSource().sendFeedback(() -> Text.literal((cleared ? "Cleared" : "Set") + " role description for " + rolePath + ".")
			.formatted(cleared ? Formatting.YELLOW : Formatting.GREEN), true);
		return 1;
	}

	private static int runTraincartAdd(CommandContext<ServerCommandSource> ctx, String name,
			BlockPos corner1, BlockPos corner2) throws CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		TrainPreset preset = loadTrainPreset(src, name);
		if (preset == null) return 0;
		int index = preset.addTrainCart(corner1, corner2);
		try {
			TrainPresetStorage.save(src.getServer(), name, preset);
			GexpressPresetsSyncHandler.broadcastTrainPresets(src.getServer());
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to save train preset: " + e.getMessage()));
			return 0;
		}
		final int cartIndex = index;
		src.sendFeedback(() -> Text.literal("Added train cart #" + cartIndex + " to preset '" + name + "'.")
			.formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int runTraincartList(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		TrainPreset preset = loadTrainPreset(src, name);
		if (preset == null) return 0;
		if (preset.trainCarts == null || preset.trainCarts.isEmpty()) {
			src.sendFeedback(() -> Text.literal("No train carts in preset '" + name + "'."), false);
			return 1;
		}
		for (int i = 0; i < preset.trainCarts.size(); i++) {
			final int index = i + 1;
			final String marker = index == 1 ? " front" : index == preset.trainCarts.size() ? " back" : "";
			final String box = boxStr(preset.trainCarts.get(i).area);
			src.sendFeedback(() -> Text.literal("#" + index + marker + " " + box), false);
		}
		return preset.trainCarts.size();
	}

	private static int runTraincartRemove(CommandContext<ServerCommandSource> ctx, String name, int oneBasedIndex) {
		ServerCommandSource src = ctx.getSource();
		TrainPreset preset = loadTrainPreset(src, name);
		if (preset == null) return 0;
		if (!preset.removeTrainCart(oneBasedIndex)) {
			src.sendError(Text.literal("No train cart #" + oneBasedIndex + " in preset '" + name + "'."));
			return 0;
		}
		try {
			TrainPresetStorage.save(src.getServer(), name, preset);
			GexpressPresetsSyncHandler.broadcastTrainPresets(src.getServer());
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to save train preset: " + e.getMessage()));
			return 0;
		}
		src.sendFeedback(() -> Text.literal("Removed train cart #" + oneBasedIndex + " from preset '" + name + "'.")
			.formatted(Formatting.YELLOW), true);
		return 1;
	}

	private static int runTraincartClear(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		TrainPreset preset = loadTrainPreset(src, name);
		if (preset == null) return 0;
		preset.clearTrainCarts();
		try {
			TrainPresetStorage.save(src.getServer(), name, preset);
			GexpressPresetsSyncHandler.broadcastTrainPresets(src.getServer());
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to save train preset: " + e.getMessage()));
			return 0;
		}
		src.sendFeedback(() -> Text.literal("Cleared train carts for preset '" + name + "'.")
			.formatted(Formatting.YELLOW), true);
		return 1;
	}

	private static TrainPreset loadTrainPreset(ServerCommandSource src, String name) {
		if (!TrainPresetStorage.isValidName(name)) {
			src.sendError(Text.literal("Invalid train preset name."));
			return null;
		}
		try {
			TrainPreset preset = TrainPresetStorage.load(src.getServer(), name);
			if (preset == null) {
				src.sendError(Text.literal("Train preset '" + name + "' does not exist."));
				return null;
			}
			return preset;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to load train preset: " + e.getMessage()));
			return null;
		}
	}

	private static CompletableFuture<Suggestions> suggestRolePaths(CommandContext<ServerCommandSource> ctx,
			SuggestionsBuilder builder) {
		String remaining = builder.getRemainingLowerCase();
		for (String role : ROLE_PATHS) {
			if (role.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(role);
		}
		return builder.buildFuture();
	}

	private static void syncConfig(ServerCommandSource src) {
		GexpressConfig.save();
		GexpressConfigSyncHandler.broadcastConfig(src.getServer());
	}

	private static String boxStr(MapPreset.BoxData b) {
		if (b == null) return "(not set)";
		return "(" + fmt(b.minX) + ", " + fmt(b.minY) + ", " + fmt(b.minZ) + ") -> ("
			+ fmt(b.maxX) + ", " + fmt(b.maxY) + ", " + fmt(b.maxZ) + ")";
	}

	private static String fmt(double d) {
		if (d == Math.floor(d) && !Double.isInfinite(d)) return Long.toString((long) d);
		return String.format(Locale.ROOT, "%.1f", d);
	}

	private static String format(float value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}
}
