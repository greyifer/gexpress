package dev.mapselect.client.screen;

import cat.rezelyn.watheextended.client.screen.GuidebookScreen;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
public final class GexpressDevCategory {
	private GexpressDevCategory() {}

	public static ConfigCategory build(Screen parent) {
		return ConfigCategory.createBuilder()
			.name(Text.translatable("gui.gexpress.config.category.dev"))
			.tooltip(Text.translatable("gui.gexpress.config.category.dev.tooltip"))
			.group(xpTuningGroup())
			.group(levelXpOverridesOption())
			.group(levelRewardRoadmapOption())
			.group(grenadePassThroughBlocksOption())
			.group(c4BackModelGroup())
			.group(spyBugModelGroup())
			.group(modelPlacementEditorGroup())
			.group(modelDefaultsExportGroup())
			.group(c4PlacementPresetsOption())
			.group(roleDescriptionsGroup())
			.group(shortSightedGroup())
			.group(medicShieldVisualsGroup())
			.group(silentShadowVisualsGroup())
			.group(tagEditorGroup())
			.group(endScreenLayoutGroup())
			.build();
	}

	private static OptionGroup xpTuningGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.xp_tuning"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.xp_tuning.tooltip")))
			.collapsed(false)
			.option(intOption("level_round_xp", 25, GexpressConfig::getLevelRoundXp,
				v -> GexpressConfig.levelRoundXp = v,
				GexpressConfig.LEVEL_XP_AMOUNT_MIN, GexpressConfig.LEVEL_XP_AMOUNT_MAX))
			.option(intOption("level_win_xp", 25, GexpressConfig::getLevelWinXp,
				v -> GexpressConfig.levelWinXp = v,
				GexpressConfig.LEVEL_XP_AMOUNT_MIN, GexpressConfig.LEVEL_XP_AMOUNT_MAX))
			.option(intOption("level_neutral_win_bonus_xp", 25, GexpressConfig::getLevelNeutralWinBonusXp,
				v -> GexpressConfig.levelNeutralWinBonusXp = v,
				GexpressConfig.LEVEL_XP_AMOUNT_MIN, GexpressConfig.LEVEL_XP_AMOUNT_MAX))
			.option(intOption("level_kill_xp", 10, GexpressConfig::getLevelKillXp,
				v -> GexpressConfig.levelKillXp = v,
				GexpressConfig.LEVEL_XP_AMOUNT_MIN, GexpressConfig.LEVEL_XP_AMOUNT_MAX))
			.option(intOption("level_civilian_task_xp", 5, GexpressConfig::getLevelCivilianTaskXp,
				v -> GexpressConfig.levelCivilianTaskXp = v,
				GexpressConfig.LEVEL_XP_AMOUNT_MIN, GexpressConfig.LEVEL_XP_AMOUNT_MAX))
			.option(intOption("level_base_xp", 100, GexpressConfig::getLevelBaseXp,
				v -> GexpressConfig.levelBaseXp = v,
				GexpressConfig.LEVEL_XP_REQUIRED_MIN, GexpressConfig.LEVEL_XP_REQUIRED_MAX))
			.option(intOption("level_xp_increase", 100, GexpressConfig::getLevelXpIncrease,
				v -> GexpressConfig.levelXpIncrease = v,
				0, GexpressConfig.LEVEL_XP_REQUIRED_MAX))
			.option(intOption("level_roadmap_display_levels", 25, GexpressConfig::getLevelRoadmapDisplayLevels,
				v -> GexpressConfig.levelRoadmapDisplayLevels = v,
				GexpressConfig.LEVEL_ROADMAP_DISPLAY_MIN, GexpressConfig.LEVEL_ROADMAP_DISPLAY_MAX))
			.build();
	}

	private static ListOption<String> levelXpOverridesOption() {
		return stringListOption("level_xp_overrides", GexpressConfig::getLevelXpOverrideStrings,
			GexpressConfig::setLevelXpOverrideStrings, () -> "5=750");
	}

	private static ListOption<String> levelRewardRoadmapOption() {
		return stringListOption("level_reward_roadmap", GexpressConfig::getLevelRewardRoadmapStrings,
			GexpressConfig::setLevelRewardRoadmapStrings,
			() -> "5|Gold Skin|Unlocks or notes the reward for reaching level 5.|g skin grant {player} revolver gold");
	}

	private static ListOption<String> grenadePassThroughBlocksOption() {
		return stringListOption("grenade_los_pass_through_blocks",
			GexpressConfig::getGrenadeLineOfSightPassThroughBlockStrings,
			GexpressConfig::setGrenadeLineOfSightPassThroughBlockStrings,
			() -> "minecraft:glass");
	}

	private static OptionGroup c4BackModelGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.c4_back_model"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.c4_back_model.tooltip")))
			.collapsed(false)
			.option(floatOption("c4_back_offset_x", 0.0F, GexpressConfig::getC4BackOffsetX,
				v -> GexpressConfig.c4BackOffsetX = v, GexpressConfig.C4_BACK_OFFSET_MIN, GexpressConfig.C4_BACK_OFFSET_MAX))
			.option(floatOption("c4_back_offset_y", 0.24F, GexpressConfig::getC4BackOffsetY,
				v -> GexpressConfig.c4BackOffsetY = v, GexpressConfig.C4_BACK_OFFSET_MIN, GexpressConfig.C4_BACK_OFFSET_MAX))
			.option(floatOption("c4_back_offset_z", 0.28F, GexpressConfig::getC4BackOffsetZ,
				v -> GexpressConfig.c4BackOffsetZ = v, GexpressConfig.C4_BACK_OFFSET_MIN, GexpressConfig.C4_BACK_OFFSET_MAX))
			.option(floatOption("c4_back_rotation_x", 0.0F, GexpressConfig::getC4BackRotationX,
				v -> GexpressConfig.c4BackRotationX = v, GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX))
			.option(floatOption("c4_back_rotation_y", 0.0F, GexpressConfig::getC4BackRotationY,
				v -> GexpressConfig.c4BackRotationY = v, GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX))
			.option(floatOption("c4_back_rotation_z", 0.0F, GexpressConfig::getC4BackRotationZ,
				v -> GexpressConfig.c4BackRotationZ = v, GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX))
			.option(floatOption("c4_back_slant", 0.0F, GexpressConfig::getC4BackSlant,
				v -> GexpressConfig.c4BackSlant = v, GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX))
			.option(floatOption("c4_back_scale", 0.42F, GexpressConfig::getC4BackScale,
				v -> GexpressConfig.c4BackScale = v, GexpressConfig.C4_BACK_SCALE_MIN, GexpressConfig.C4_BACK_SCALE_MAX))
			.build();
	}

	private static ListOption<String> c4PlacementPresetsOption() {
		return ListOption.<String>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.dev.c4_placement_presets"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.dev.c4_placement_presets.tooltip")))
			.binding(List.of(), GexpressConfig::getC4PlacementPresetStrings, values -> {
				GexpressConfig.setC4PlacementPresetStrings(values);
				GexpressOptionsScreen.pushGexpressConfigToServer();
			})
			.controller(StringControllerBuilder::create)
			.initial(GexpressConfig::getCurrentC4PlacementPresetString)
			.collapsed(false)
			.build();
	}

	private static OptionGroup spyBugModelGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.spy_bug_model"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.spy_bug_model.tooltip")))
			.collapsed(false)
			.option(floatOption("spy_bug_offset_x", 0.0F, GexpressConfig::getSpyBugOffsetX,
				v -> GexpressConfig.spyBugOffsetX = v, GexpressConfig.C4_BACK_OFFSET_MIN, GexpressConfig.C4_BACK_OFFSET_MAX))
			.option(floatOption("spy_bug_offset_y", 0.16F, GexpressConfig::getSpyBugOffsetY,
				v -> GexpressConfig.spyBugOffsetY = v, GexpressConfig.C4_BACK_OFFSET_MIN, GexpressConfig.C4_BACK_OFFSET_MAX))
			.option(floatOption("spy_bug_offset_z", 0.31F, GexpressConfig::getSpyBugOffsetZ,
				v -> GexpressConfig.spyBugOffsetZ = v, GexpressConfig.C4_BACK_OFFSET_MIN, GexpressConfig.C4_BACK_OFFSET_MAX))
			.option(floatOption("spy_bug_rotation_x", 0.0F, GexpressConfig::getSpyBugRotationX,
				v -> GexpressConfig.spyBugRotationX = v, GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX))
			.option(floatOption("spy_bug_rotation_y", 0.0F, GexpressConfig::getSpyBugRotationY,
				v -> GexpressConfig.spyBugRotationY = v, GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX))
			.option(floatOption("spy_bug_rotation_z", 0.0F, GexpressConfig::getSpyBugRotationZ,
				v -> GexpressConfig.spyBugRotationZ = v, GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX))
			.option(floatOption("spy_bug_slant", 0.0F, GexpressConfig::getSpyBugSlant,
				v -> GexpressConfig.spyBugSlant = v, GexpressConfig.C4_BACK_ROTATION_MIN, GexpressConfig.C4_BACK_ROTATION_MAX))
			.option(floatOption("spy_bug_scale", 0.28F, GexpressConfig::getSpyBugScale,
				v -> GexpressConfig.spyBugScale = v, GexpressConfig.C4_BACK_SCALE_MIN, GexpressConfig.C4_BACK_SCALE_MAX))
			.build();
	}

	private static OptionGroup modelDefaultsExportGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.model_defaults_export"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.model_defaults_export.tooltip")))
			.collapsed(false)
			.option(ButtonOption.createBuilder()
				.name(Text.translatable("gui.gexpress.config.option.dev.model_defaults_export"))
				.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.dev.model_defaults_export.tooltip")))
				.text(Text.translatable("gui.gexpress.config.option.dev.model_defaults_export.copy"))
				.action((screen, option) -> copyModelDefaultsToClipboard())
				.build())
			.build();
	}

	private static OptionGroup modelPlacementEditorGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.model_placement_editor"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.model_placement_editor.tooltip")))
			.collapsed(false)
			.option(ButtonOption.createBuilder()
				.name(Text.translatable("gui.gexpress.config.option.dev.model_placement_editor"))
				.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.dev.model_placement_editor.tooltip")))
				.text(Text.translatable("gui.gexpress.config.option.dev.model_placement_editor.open"))
				.action((screen, option) -> MinecraftClient.getInstance()
					.setScreen(new GexpressModelPlacementScreen(screen)))
				.build())
			.build();
	}

	private static void copyModelDefaultsToClipboard() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) return;
		client.keyboard.setClipboard(modelDefaultsSource());
		if (client.player != null) {
			client.player.sendMessage(Text.translatable("gui.gexpress.config.option.dev.model_defaults_export.copied")
				.formatted(Formatting.GREEN), false);
		}
	}

	private static String modelDefaultsSource() {
		List<String> lines = new ArrayList<>();
		lines.add("// Paste into GexpressConfig model default fields.");
		lines.add(defaultLine("c4BackOffsetX", GexpressConfig.getC4BackOffsetX()));
		lines.add(defaultLine("c4BackOffsetY", GexpressConfig.getC4BackOffsetY()));
		lines.add(defaultLine("c4BackOffsetZ", GexpressConfig.getC4BackOffsetZ()));
		lines.add(defaultLine("c4BackRotationX", GexpressConfig.getC4BackRotationX()));
		lines.add(defaultLine("c4BackRotationY", GexpressConfig.getC4BackRotationY()));
		lines.add(defaultLine("c4BackRotationZ", GexpressConfig.getC4BackRotationZ()));
		lines.add(defaultLine("c4BackSlant", GexpressConfig.getC4BackSlant()));
		lines.add(defaultLine("c4BackScale", GexpressConfig.getC4BackScale()));
		lines.add(defaultLine("spyBugOffsetX", GexpressConfig.getSpyBugOffsetX()));
		lines.add(defaultLine("spyBugOffsetY", GexpressConfig.getSpyBugOffsetY()));
		lines.add(defaultLine("spyBugOffsetZ", GexpressConfig.getSpyBugOffsetZ()));
		lines.add(defaultLine("spyBugRotationX", GexpressConfig.getSpyBugRotationX()));
		lines.add(defaultLine("spyBugRotationY", GexpressConfig.getSpyBugRotationY()));
		lines.add(defaultLine("spyBugRotationZ", GexpressConfig.getSpyBugRotationZ()));
		lines.add(defaultLine("spyBugSlant", GexpressConfig.getSpyBugSlant()));
		lines.add(defaultLine("spyBugScale", GexpressConfig.getSpyBugScale()));
		lines.add("");
		lines.add("// Paste into the c4PlacementPresets field if these surface presets are final.");
		lines.add("public static List<String> c4PlacementPresets = new ArrayList<>(List.of(");
		List<String> presets = GexpressConfig.getC4PlacementPresetStrings();
		for (int i = 0; i < presets.size(); i++) {
			String suffix = i == presets.size() - 1 ? "" : ",";
			lines.add("\t\"" + presets.get(i).replace("\\", "\\\\").replace("\"", "\\\"") + "\"" + suffix);
		}
		lines.add("));");
		return String.join("\n", lines);
	}

	private static String defaultLine(String name, float value) {
		return "public static float " + name + " = " + format(value) + "F;";
	}

	private static OptionGroup roleDescriptionsGroup() {
		OptionGroup.Builder group = OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.role_descriptions"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.role_descriptions.tooltip")))
			.collapsed(true);
		for (String role : List.of("bomb_specialist", "medic", "snitch", "seer", "time_master",
				"the_silent", "warlock", "juggernaut", "trickster", "puppetmaster", "bounty_hunter",
				"pelican", "scatter_brain", "skincrawler", "tracker", "spy", "altruist",
				"godfather", "mafioso", "janitor")) {
			group.option(roleDescriptionOption(role));
		}
		return group.build();
	}

	private static Option<String> roleDescriptionOption(String rolePath) {
		return Option.<String>createBuilder()
			.name(Text.translatable("announcement.role.gexpress." + rolePath))
			.description(DynamicOptionDescription.of(() -> roleDescriptionHoverLines(rolePath)))
			.binding(currentRoleDescription(rolePath), () -> currentRoleDescription(rolePath),
				value -> saveRoleDescriptionOverride(rolePath, value))
			.controller(StringControllerBuilder::create)
			.instant(true)
			.build();
	}

	private static List<Text> roleDescriptionHoverLines(String rolePath) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.translatable("announcement.role.gexpress." + rolePath).formatted(Formatting.GOLD, Formatting.BOLD));
		for (String line : currentRoleDescription(rolePath).split("\\\\n|\\n")) {
			if (!line.isEmpty()) {
				lines.add(Text.literal(line).formatted(Formatting.WHITE));
			}
		}
		lines.add(Text.literal(""));
		lines.add(Text.translatable("gui.gexpress.config.option.dev.role_description.tooltip")
			.formatted(Formatting.GRAY));
		return lines;
	}

	private static String currentRoleDescription(String rolePath) {
		String override = GexpressConfig.getRoleDescriptionOverride(rolePath);
		if (override != null && !override.isBlank()) return override;
		return builtInRoleDescription(rolePath);
	}

	private static String builtInRoleDescription(String rolePath) {
		String key = "gui.watheextended.guidebook.role.desc.gexpress." + rolePath;
		String resolved = Text.translatable(key).getString();
		return resolved == null || resolved.equals(key) ? "" : resolved;
	}

	private static void saveRoleDescriptionOverride(String rolePath, String value) {
		String cleaned = value == null ? "" : value.strip();
		String builtIn = builtInRoleDescription(rolePath).strip();
		GexpressConfig.setRoleDescriptionOverride(rolePath,
			cleaned.isEmpty() || cleaned.equals(builtIn) ? "" : cleaned);
		GuidebookScreen.invalidateIfOpen();
		GexpressOptionsScreen.pushGexpressConfigToServer();
	}

	private static OptionGroup shortSightedGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.short_sighted"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.short_sighted.tooltip")))
			.collapsed(false)
			.option(floatOption("short_sighted_entity_range", 5.0F, GexpressConfig::getShortSightedEntityRange,
				v -> GexpressConfig.shortSightedFogRange = v,
				GexpressConfig.SHORT_SIGHTED_ENTITY_RANGE_MIN, GexpressConfig.SHORT_SIGHTED_ENTITY_RANGE_MAX))
			.build();
	}

	private static OptionGroup medicShieldVisualsGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.medic_shield_visuals"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.medic_shield_visuals.tooltip")))
			.collapsed(true)
			.option(intOption("medic_shield_block_flash_ticks", 18, GexpressConfig::getMedicShieldBlockFlashTicks,
				v -> GexpressConfig.medicShieldBlockFlashTicks = v,
				GexpressConfig.MEDIC_SHIELD_FLASH_TICKS_MIN, GexpressConfig.MEDIC_SHIELD_FLASH_TICKS_MAX))
			.option(intOption("medic_shield_break_flash_ticks", 28, GexpressConfig::getMedicShieldBreakFlashTicks,
				v -> GexpressConfig.medicShieldBreakFlashTicks = v,
				GexpressConfig.MEDIC_SHIELD_FLASH_TICKS_MIN, GexpressConfig.MEDIC_SHIELD_FLASH_TICKS_MAX))
			.option(intOption("medic_shield_block_flash_alpha", 72, GexpressConfig::getMedicShieldBlockFlashAlpha,
				v -> GexpressConfig.medicShieldBlockFlashAlpha = v,
				GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MIN, GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MAX))
			.option(intOption("medic_shield_break_flash_alpha", 92, GexpressConfig::getMedicShieldBreakFlashAlpha,
				v -> GexpressConfig.medicShieldBreakFlashAlpha = v,
				GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MIN, GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MAX))
			.build();
	}

	private static OptionGroup silentShadowVisualsGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.silent_shadow"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.silent_shadow.tooltip")))
			.collapsed(false)
			.option(floatOption("silent_shadow_alpha", 0.45F, GexpressConfig::getSilentShadowAlpha,
				v -> GexpressConfig.silentShadowAlpha = v,
				GexpressConfig.SILENT_SHADOW_ALPHA_MIN, GexpressConfig.SILENT_SHADOW_ALPHA_MAX))
			.build();
	}

	private static OptionGroup endScreenLayoutGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.end_screen_layout"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.end_screen_layout.tooltip")))
			.collapsed(false)
			.option(ButtonOption.createBuilder()
				.name(Text.translatable("gui.gexpress.config.option.dev.end_screen_layout"))
				.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.dev.end_screen_layout.tooltip")))
				.text(Text.translatable("gui.gexpress.config.option.dev.end_screen_layout.open"))
				.action((screen, option) -> MinecraftClient.getInstance()
					.setScreen(new GexpressEndScreenLayoutScreen(screen)))
				.build())
			.build();
	}

	private static OptionGroup tagEditorGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.tag_editor"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.tag_editor.tooltip")))
			.collapsed(false)
			.option(ButtonOption.createBuilder()
				.name(Text.translatable("gui.gexpress.config.option.dev.tag_editor"))
				.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.dev.tag_editor.tooltip")))
				.text(Text.translatable("gui.gexpress.config.option.dev.tag_editor.open"))
				.action((screen, option) -> MinecraftClient.getInstance()
					.setScreen(new GexpressTagEditorScreen(screen)))
				.build())
			.build();
	}

	private static ListOption<String> stringListOption(String key, Supplier<List<String>> getter,
			Consumer<List<String>> setter, Supplier<String> initial) {
		return ListOption.<String>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.dev." + key))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.dev." + key + ".tooltip")))
			.binding(List.of(), getter, values -> {
				setter.accept(values);
				GexpressOptionsScreen.pushGexpressConfigToServer();
			})
			.controller(StringControllerBuilder::create)
			.initial(initial)
			.collapsed(false)
			.build();
	}

	private static Option<String> floatOption(String key, float defaultValue, Supplier<Float> getter,
			Consumer<Float> setter, float min, float max) {
		return Option.<String>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.dev." + key))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.dev." + key + ".tooltip")))
			.binding(format(defaultValue), () -> format(getter.get()), value -> parseAndApply(value, setter, min, max))
			.controller(StringControllerBuilder::create)
			.instant(true)
			.build();
	}

	private static Option<Integer> intOption(String key, int defaultValue, Supplier<Integer> getter,
			Consumer<Integer> setter, int min, int max) {
		return Option.<Integer>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.dev." + key))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.dev." + key + ".tooltip")))
			.binding(defaultValue, getter, value -> {
				setter.accept(value);
				GexpressOptionsScreen.pushGexpressConfigToServer();
			})
			.controller(opt -> IntegerFieldControllerBuilder.create(opt).range(min, max))
			.instant(true)
			.build();
	}

	private static String format(float value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private static void parseAndApply(String raw, Consumer<Float> setter, float min, float max) {
		Float parsed = parseFloat(raw);
		if (parsed == null) return;
		setter.accept(Math.max(min, Math.min(max, parsed)));
		GexpressOptionsScreen.pushGexpressConfigToServer();
	}

	private static Float parseFloat(String raw) {
		if (raw == null) return null;
		String normalized = raw.trim().replace(',', '.');
		if (normalized.isEmpty() || normalized.equals("-") || normalized.equals(".") || normalized.equals("-.")) {
			return null;
		}
		try {
			float value = Float.parseFloat(normalized);
			return Float.isFinite(value) ? value : null;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

}
