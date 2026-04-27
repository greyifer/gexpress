package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.mapselect.client.preset.ClientPresetCache;
import dev.mapselect.preset.map.MapPreset;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;

public final class GexpressMapScreen {
	private GexpressMapScreen() {}

	public static ConfigCategory buildListCategory(Screen parent) {
		ConfigCategory.Builder cat = ConfigCategory.createBuilder()
			.name(Text.translatable("gui.gexpress.config.category.maps"))
			.tooltip(Text.translatable("gui.gexpress.config.category.maps.tooltip"));

		OptionGroup.Builder group = OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.maps.list").formatted(Formatting.GOLD))
			.description(OptionDescription.of(
				Text.translatable("gui.gexpress.config.group.maps.list.tooltip").formatted(Formatting.GRAY)));

		Map<String, MapPreset> presets = ClientPresetCache.snapshot();
		if (presets.isEmpty()) {
			group.option(LabelOption.create(
				Text.translatable("gui.gexpress.config.group.maps.empty.tooltip").formatted(Formatting.DARK_GRAY)));
		} else {
			for (String name : presets.keySet()) {
				group.option(ButtonOption.createBuilder()
					.name(Text.literal(name).formatted(Formatting.GOLD))
					.text(Text.translatable("gui.gexpress.config.maps.edit").formatted(Formatting.AQUA))
					.action((scr, opt) -> GexpressOptionsScreen.navigateMaps(parent, name))
					.build());
			}
		}

		cat.group(group.build());
		return cat.build();
	}
}
