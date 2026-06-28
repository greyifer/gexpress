package dev.mapselect.command.admin;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.mapselect.command.setup.TrainCommand;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.level.LevelComponent;
import dev.mapselect.network.config.GexpressConfigSyncHandler;
import dev.mapselect.network.preset.GexpressPresetsSyncHandler;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.train.TrainPreset;
import dev.mapselect.preset.train.TrainPresetStorage;
import dev.mapselect.role.bombspecialist.C4PlacementPreset;
import dev.mapselect.role.painter.PainterManager;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
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
		return appendTo(CommandManager.literal("dev"));
	}

	public static LiteralArgumentBuilder<ServerCommandSource> appendTo(LiteralArgumentBuilder<ServerCommandSource> root) {
		SuggestionProvider<ServerCommandSource> rolePaths = DevCommand::suggestRolePaths;
		SuggestionProvider<ServerCommandSource> trainNames = TrainCommand::suggestTrainNames;

		return root
			.then(levelCommandTree())
			.then(adminDevLiteral("c4back")
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
			.then(adminDevLiteral("c4preset")
				.then(CommandManager.literal("add")
					.executes(ctx -> runC4PresetAdd(ctx, GexpressConfig.getCurrentC4PlacementPresetString()))
					.then(CommandManager.argument("values", StringArgumentType.greedyString())
						.executes(ctx -> runC4PresetAdd(ctx, StringArgumentType.getString(ctx, "values")))))
				.then(CommandManager.literal("list").executes(DevCommand::runC4PresetList))
				.then(CommandManager.literal("remove")
					.then(CommandManager.argument("index", IntegerArgumentType.integer(1))
						.executes(ctx -> runC4PresetRemove(ctx, IntegerArgumentType.getInteger(ctx, "index")))))
				.then(CommandManager.literal("clear").executes(DevCommand::runC4PresetClear)))
			.then(adminDevLiteral("roledesc")
				.then(CommandManager.argument("role", StringArgumentType.word())
					.suggests(rolePaths)
					.then(CommandManager.literal("set")
						.then(CommandManager.argument("description", StringArgumentType.greedyString())
							.executes(ctx -> runRoleDescSet(ctx,
								StringArgumentType.getString(ctx, "role"),
								StringArgumentType.getString(ctx, "description")))))
					.then(CommandManager.literal("clear")
						.executes(ctx -> runRoleDescSet(ctx, StringArgumentType.getString(ctx, "role"), "")))))
			.then(adminDevLiteral("shortsighted")
				.then(CommandManager.literal("range")
					.then(CommandManager.argument("value", FloatArgumentType.floatArg(
							GexpressConfig.SHORT_SIGHTED_ENTITY_RANGE_MIN,
							GexpressConfig.SHORT_SIGHTED_ENTITY_RANGE_MAX))
						.executes(ctx -> runFloatSet(ctx, "shortsighted range",
							FloatArgumentType.getFloat(ctx, "value"),
							v -> GexpressConfig.shortSightedFogRange = v,
							GexpressConfig::getShortSightedEntityRange)))))
			.then(adminDevLiteral("medicshield")
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
			.then(adminDevLiteral("silentshadow")
				.then(CommandManager.literal("alpha")
					.then(CommandManager.argument("value", FloatArgumentType.floatArg(
							GexpressConfig.SILENT_SHADOW_ALPHA_MIN,
							GexpressConfig.SILENT_SHADOW_ALPHA_MAX))
						.executes(ctx -> runFloatSet(ctx, "silent shadow alpha",
							FloatArgumentType.getFloat(ctx, "value"),
							v -> GexpressConfig.silentShadowAlpha = v,
							GexpressConfig::getSilentShadowAlpha)))))
			.then(adminDevLiteral("painterdoor")
				.then(CommandManager.literal("toggle")
					.executes(ctx -> runPainterDoorToggle(ctx, null)))
				.then(CommandManager.literal("disable")
					.executes(ctx -> runPainterDoorToggle(ctx, true)))
				.then(CommandManager.literal("enable")
					.executes(ctx -> runPainterDoorToggle(ctx, false)))
				.then(CommandManager.literal("clear")
					.executes(DevCommand::runPainterDoorClear)))
			.then(adminDevLiteral("traincart")
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

	private static LiteralArgumentBuilder<ServerCommandSource> levelCommandTree() {
		return CommandManager.literal("level")
			.requires(DevCommand::canUseLevelBranch)
			.then(CommandManager.literal("xp")
				.requires(source -> canUseLevelCommand(source, "xp"))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
						.executes(ctx -> runLevelXp(ctx,
							GameProfileArgumentType.getProfileArgument(ctx, "players"),
							IntegerArgumentType.getInteger(ctx, "amount"))))))
			.then(CommandManager.literal("level")
				.requires(source -> canUseLevelCommand(source, "level"))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.then(CommandManager.argument("level", IntegerArgumentType.integer(1))
						.executes(ctx -> runLevelSet(ctx,
							GameProfileArgumentType.getProfileArgument(ctx, "players"),
							IntegerArgumentType.getInteger(ctx, "level"))))))
			.then(CommandManager.literal("reset")
				.requires(source -> canUseLevelCommand(source, "reset"))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(ctx -> runLevelReset(ctx,
						GameProfileArgumentType.getProfileArgument(ctx, "players")))))
			.then(CommandManager.literal("rewards")
				.requires(source -> canUseLevelCommand(source, "rewards"))
				.then(CommandManager.literal("reset")
					.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
						.executes(ctx -> runLevelRewardResetAll(ctx,
							GameProfileArgumentType.getProfileArgument(ctx, "players")))
						.then(CommandManager.argument("level", IntegerArgumentType.integer(1))
							.executes(ctx -> runLevelRewardResetLevel(ctx,
								GameProfileArgumentType.getProfileArgument(ctx, "players"),
								IntegerArgumentType.getInteger(ctx, "level")))))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> adminDevLiteral(String name) {
		return CommandManager.literal(name)
			.requires(source -> DEV.test(source)
				|| GexpressPermissions.canUseCommandBranch(source, "admin", name)
				|| GexpressPermissions.canUseCommandBranch(source, "admin", "dev", name));
	}

	private static boolean canUseLevelBranch(ServerCommandSource source) {
		return GexpressPermissions.canManageProgression(source)
			|| GexpressPermissions.canUseCommandBranch(source, "admin", "level")
			|| GexpressPermissions.canUseCommandBranch(source, "admin", "dev", "level");
	}

	private static boolean canUseLevelCommand(ServerCommandSource source, String subcommand) {
		return GexpressPermissions.canManageProgression(source)
			|| GexpressPermissions.canUseCommandPath(source, "admin", "level", subcommand)
			|| GexpressPermissions.canUseCommandPath(source, "admin", "dev", "level", subcommand);
	}

	private static int runPainterDoorToggle(CommandContext<ServerCommandSource> ctx, Boolean disabled)
			throws CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		ServerPlayerEntity player = source.getPlayerOrThrow();
		PainterManager.DoorwayToggleResult result = PainterManager.setLookedDoorwayDisabled(player, disabled);
		return sendPainterDoorToggleFeedback(source, result);
	}

	private static int sendPainterDoorToggleFeedback(ServerCommandSource source,
			PainterManager.DoorwayToggleResult result) {
		if (!result.found()) {
			source.sendError(Text.literal("Choose a door to toggle Painter doorway destination access."));
			return 0;
		}
		source.sendFeedback(() -> Text.literal((result.disabled() ? "Disabled" : "Enabled")
				+ " Painter doorway destination at " + formatBlockPos(result.lowerPos()) + ".")
			.formatted(result.disabled() ? Formatting.YELLOW : Formatting.GREEN), true);
		return 1;
	}

	private static int runPainterDoorClear(CommandContext<ServerCommandSource> ctx) {
		int count = PainterManager.clearDisabledDoorways(ctx.getSource().getWorld());
		ctx.getSource().sendFeedback(() -> Text.literal("Cleared " + count + " disabled Painter doorway"
				+ (count == 1 ? "." : "s."))
			.formatted(Formatting.GREEN), true);
		return count;
	}

	private static String formatBlockPos(BlockPos pos) {
		return pos == null ? "unknown" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private static int runLevelXp(CommandContext<ServerCommandSource> ctx, Collection<GameProfile> profiles,
			int progress) {
		ServerCommandSource src = ctx.getSource();
		LevelComponent levels = LevelComponent.KEY.get(src.getWorld());
		int changed = 0;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			int currentLevel = levels.level(profile.getId());
			int totalXp = LevelComponent.totalXpForLevel(currentLevel) + progress;
			if (levels.setXp(profile.getId(), totalXp)) {
				refreshLevelDisplay(src, profile);
				changed++;
			}
		}
		final int finalChanged = changed;
		src.sendFeedback(() -> Text.literal("Set level XP progress to " + progress + " for "
				+ finalChanged + " player(s).").formatted(Formatting.GREEN), true);
		return changed;
	}

	private static int runLevelSet(CommandContext<ServerCommandSource> ctx, Collection<GameProfile> profiles,
			int level) {
		ServerCommandSource src = ctx.getSource();
		LevelComponent levels = LevelComponent.KEY.get(src.getWorld());
		int changed = 0;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			if (levels.setLevel(profile.getId(), level)) {
				refreshLevelDisplay(src, profile);
				changed++;
			}
		}
		final int finalChanged = changed;
		src.sendFeedback(() -> Text.literal("Set level to " + level + " for "
				+ finalChanged + " player(s).").formatted(Formatting.GREEN), true);
		return changed;
	}

	private static int runLevelReset(CommandContext<ServerCommandSource> ctx, Collection<GameProfile> profiles) {
		ServerCommandSource src = ctx.getSource();
		LevelComponent levels = LevelComponent.KEY.get(src.getWorld());
		int changed = 0;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			levels.resetPlayer(profile.getId());
			refreshLevelDisplay(src, profile);
			changed++;
		}
		final int finalChanged = changed;
		src.sendFeedback(() -> Text.literal("Reset level, XP, and claimed roadmap rewards for "
				+ finalChanged + " player(s).").formatted(Formatting.YELLOW), true);
		return changed;
	}

	private static int runLevelRewardResetAll(CommandContext<ServerCommandSource> ctx,
			Collection<GameProfile> profiles) {
		ServerCommandSource src = ctx.getSource();
		LevelComponent levels = LevelComponent.KEY.get(src.getWorld());
		int changed = 0;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			levels.resetClaimedRewards(profile.getId());
			changed++;
		}
		final int finalChanged = changed;
		src.sendFeedback(() -> Text.literal("Reset claimed XP Roadmap rewards for "
				+ finalChanged + " player(s).").formatted(Formatting.YELLOW), true);
		return changed;
	}

	private static int runLevelRewardResetLevel(CommandContext<ServerCommandSource> ctx,
			Collection<GameProfile> profiles, int level) {
		ServerCommandSource src = ctx.getSource();
		LevelComponent levels = LevelComponent.KEY.get(src.getWorld());
		int changed = 0;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			levels.resetClaimedReward(profile.getId(), level);
			changed++;
		}
		final int finalChanged = changed;
		src.sendFeedback(() -> Text.literal("Reset level " + level + " XP Roadmap reward claim for "
				+ finalChanged + " player(s).").formatted(Formatting.YELLOW), true);
		return changed;
	}

	private static void refreshLevelDisplay(ServerCommandSource src, GameProfile profile) {
		ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(profile.getId());
		if (online != null) TagCommand.refreshPlayerListName(online);
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
