package dev.mapselect.client.screen;

import cat.rezelyn.watheextended.client.screen.config.ClientCategory;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class GexpressClientCategory {
	private GexpressClientCategory() {}

	public static ConfigCategory build(Screen parent, boolean isOp, BiConsumer<String, Screen> stage) {
		ConfigCategory base = ClientCategory.build(parent, isOp, stage);
		ConfigCategory.Builder category = ConfigCategory.createBuilder()
			.name(base.name())
			.tooltip(base.tooltip());
		for (OptionGroup group : base.groups()) {
			category.group(group);
		}
		category.group(weaponSkinsGroup());
		category.group(abilityHudGroup());
		return category.build();
	}

	private static OptionGroup weaponSkinsGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.client.weapon_skins"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.client.weapon_skins.tooltip")))
			.collapsed(false)
			.option(Option.<Boolean>createBuilder()
				.name(Text.translatable("gui.gexpress.config.option.client.use_3d_gun_skins"))
				.description(OptionDescription.of(
					Text.translatable("gui.gexpress.config.option.client.use_3d_gun_skins.tooltip")))
				.binding(true, GexpressConfig::use3dGunSkins, value -> GexpressConfig.use3dGunSkins = value)
				.controller(BooleanControllerBuilder::create)
				.instant(true)
				.build())
			.build();
	}

	private static OptionGroup abilityHudGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.client.ability_hud"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.client.ability_hud.tooltip")))
			.collapsed(false)
			.option(intSlider("ability_hud_scale", 100, GexpressConfig::getAbilityHudScalePercent,
				v -> GexpressConfig.abilityHudScalePercent = v,
				GexpressConfig.ABILITY_HUD_SCALE_MIN, GexpressConfig.ABILITY_HUD_SCALE_MAX, 5,
				value -> value + "%"))
			.option(intSlider("ability_hud_offset_x", 0, GexpressConfig::getAbilityHudOffsetX,
				v -> GexpressConfig.abilityHudOffsetX = v,
				GexpressConfig.ABILITY_HUD_OFFSET_MIN, GexpressConfig.ABILITY_HUD_OFFSET_MAX, 1,
				GexpressClientCategory::formatPixels))
			.option(intSlider("ability_hud_offset_y", 0, GexpressConfig::getAbilityHudOffsetY,
				v -> GexpressConfig.abilityHudOffsetY = v,
				GexpressConfig.ABILITY_HUD_OFFSET_MIN, GexpressConfig.ABILITY_HUD_OFFSET_MAX, 1,
				GexpressClientCategory::formatPixels))
			.build();
	}

	private static Option<Integer> intSlider(String key, int defaultValue, Supplier<Integer> getter,
			java.util.function.IntConsumer setter, int min, int max, int step,
			java.util.function.Function<Integer, String> formatter) {
		return Option.<Integer>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.client." + key))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.client." + key + ".tooltip")))
			.binding(defaultValue, getter, value -> setter.accept(value))
			.controller(opt -> IntegerSliderControllerBuilder.create(opt)
				.range(min, max)
				.step(step)
				.formatValue(value -> Text.literal(formatter.apply(value))))
			.instant(true)
			.build();
	}

	private static String formatPixels(int value) {
		return (value > 0 ? "+" : "") + value + " px";
	}
}
