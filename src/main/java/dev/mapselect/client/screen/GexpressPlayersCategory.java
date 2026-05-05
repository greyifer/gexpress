package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GexpressPlayersCategory {
	private GexpressPlayersCategory() {}

	public static ConfigCategory build(Screen parent) {
		return ConfigCategory.createBuilder()
			.name(Text.translatable("gui.gexpress.config.category.players"))
			.tooltip(Text.translatable("gui.gexpress.config.category.players.tooltip"))
			.group(OptionGroup.createBuilder()
				.name(Text.translatable("gui.gexpress.config.group.players.live").formatted(Formatting.GOLD))
				.description(OptionDescription.of(
					Text.translatable("gui.gexpress.config.group.players.live.tooltip").formatted(Formatting.GRAY)))
				.option(ButtonOption.createBuilder()
					.name(Text.translatable("gui.gexpress.config.players.open").formatted(Formatting.AQUA))
					.text(Text.translatable("gui.gexpress.config.players.open.action").formatted(Formatting.WHITE))
					.action((screen, option) ->
						MinecraftClient.getInstance().setScreen(new GexpressPlayersScreen(screen)))
					.build())
				.build())
			.build();
	}
}
