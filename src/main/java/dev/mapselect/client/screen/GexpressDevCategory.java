package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.preset.train.TrainPresetStorage;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class GexpressDevCategory {
	private static final List<String> pendingTrainCartRows = new ArrayList<>();

	private GexpressDevCategory() {}

	public static ConfigCategory build(Screen parent) {
		return ConfigCategory.createBuilder()
			.name(Text.translatable("gui.gexpress.config.category.dev"))
			.tooltip(Text.translatable("gui.gexpress.config.category.dev.tooltip"))
			.group(c4BackModelGroup())
			.group(c4PlacementPresetsOption())
			.group(trainCartAdditionsOption())
			.group(roleDescriptionsGroup())
			.group(shortSightedGroup())
			.group(medicShieldVisualsGroup())
			.group(silentShadowVisualsGroup())
			.build();
	}

	static void clearPendingTrainCartRows() {
		pendingTrainCartRows.clear();
	}

	static void flushTrainCartCommands() {
		for (String row : pendingTrainCartRows) {
			String command = trainCartCommand(row);
			if (command != null) GexpressOptionsScreen.stageChatCommand(command);
		}
		pendingTrainCartRows.clear();
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

	private static ListOption<String> trainCartAdditionsOption() {
		return ListOption.<String>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.dev.train_cart_additions"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.dev.train_cart_additions.tooltip")))
			.binding(List.of(), () -> List.copyOf(pendingTrainCartRows), values -> {
				pendingTrainCartRows.clear();
				pendingTrainCartRows.addAll(values);
			})
			.controller(StringControllerBuilder::create)
			.initial(() -> "")
			.collapsed(true)
			.build();
	}

	private static OptionGroup roleDescriptionsGroup() {
		OptionGroup.Builder group = OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.role_descriptions"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.role_descriptions.tooltip")))
			.collapsed(true);
		for (String role : List.of("bomb_specialist", "medic", "snitch", "seer", "time_master",
				"the_silent", "warlock", "juggernaut", "trickster", "puppetmaster", "pelican")) {
			group.option(roleDescriptionOption(role));
		}
		return group.build();
	}

	private static Option<String> roleDescriptionOption(String rolePath) {
		return Option.<String>createBuilder()
			.name(Text.translatable("announcement.role.gexpress." + rolePath))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.dev.role_description.tooltip")))
			.binding("", () -> GexpressConfig.getRoleDescriptionOverride(rolePath),
				value -> {
					GexpressConfig.setRoleDescriptionOverride(rolePath, value);
					GexpressOptionsScreen.pushGexpressConfigToServer();
				})
			.controller(StringControllerBuilder::create)
			.build();
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

	private static String trainCartCommand(String raw) {
		if (raw == null || raw.isBlank()) return null;
		String[] parts = raw.trim().split("\\s+");
		if (parts.length != 7 || !TrainPresetStorage.isValidName(parts[0])) return null;
		for (int i = 1; i < parts.length; i++) {
			try {
				Integer.parseInt(parts[i]);
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return "g dev traincart " + String.join(" ", parts);
	}
}
