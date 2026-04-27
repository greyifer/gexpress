package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.mapselect.client.preset.ClientPresetCache;
import dev.mapselect.preset.map.MapPreset;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;

public final class GexpressMapDetailScreen {
	private GexpressMapDetailScreen() {}

	public static ConfigCategory buildDetailCategory(Screen parent, String presetName) {
		Map<String, MapPreset> presets = ClientPresetCache.snapshot();
		MapPreset original = presets.get(presetName);
		if (original == null) return GexpressMapScreen.buildListCategory(parent);

		ConfigCategory presetCategory = GexpressMapPresetsCategory.buildPresetCategory(presetName, original);

		ConfigCategory.Builder rebuilt = ConfigCategory.createBuilder()
			.name(Text.translatable("gui.gexpress.config.category.maps"))
			.tooltip(Text.translatable("gui.gexpress.config.group.map.tooltip", presetName));

		OptionGroup backGroup = OptionGroup.createBuilder()
			.name(Text.literal(presetName).formatted(Formatting.GOLD))
			.description(OptionDescription.of(
				Text.translatable("gui.gexpress.config.maps.back.tooltip").formatted(Formatting.GRAY)))
			.option(ButtonOption.createBuilder()
				.name(Text.literal("← ").formatted(Formatting.AQUA)
					.append(Text.translatable("gui.gexpress.config.maps.back").formatted(Formatting.AQUA)))
				.text(Text.translatable("gui.gexpress.config.maps.back.action").formatted(Formatting.WHITE))
				.action((scr, opt) -> GexpressOptionsScreen.navigateMaps(parent, null))
				.build())
			.build();
		rebuilt.group(backGroup);

		presetCategory.groups().forEach(rebuilt::group);
		return rebuilt.build();
	}
}
