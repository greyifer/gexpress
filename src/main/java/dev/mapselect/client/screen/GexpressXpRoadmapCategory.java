package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.level.LevelComponent;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@SuppressWarnings("deprecation")
public final class GexpressXpRoadmapCategory {
	private GexpressXpRoadmapCategory() {}

	public static ConfigCategory build(Screen parent) {
		ConfigCategory.Builder category = ConfigCategory.createBuilder()
			.name(Text.translatable("gui.gexpress.config.category.xp_roadmap"))
			.tooltip(Text.translatable("gui.gexpress.config.category.xp_roadmap.tooltip"))
			.group(earningGroup());

		OptionGroup.Builder roadmap = OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.xp_roadmap.levels"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.xp_roadmap.levels.tooltip")))
			.collapsed(false);

		for (int level = 1; level <= GexpressConfig.getLevelRoadmapDisplayLevels(); level++) {
			roadmap.option(levelOption(level));
		}

		return category.group(roadmap.build()).build();
	}

	private static OptionGroup earningGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.xp_roadmap.earning"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.xp_roadmap.earning.tooltip")))
			.collapsed(false)
			.option(infoOption("round", GexpressConfig.getLevelRoundXp()))
			.option(infoOption("win", GexpressConfig.getLevelWinXp()))
			.option(infoOption("neutral_win", GexpressConfig.getLevelNeutralWinBonusXp()))
			.option(infoOption("kill", GexpressConfig.getLevelKillXp()))
			.option(infoOption("civilian_task", GexpressConfig.getLevelCivilianTaskXp()))
			.build();
	}

	private static ButtonOption infoOption(String key, int amount) {
		return ButtonOption.createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.xp_roadmap." + key, amount)
				.formatted(Formatting.WHITE))
			.description(OptionDescription.of(Text.translatable(
				"gui.gexpress.config.option.xp_roadmap." + key + ".tooltip", amount)))
			.text(Text.literal("+" + amount + " XP").formatted(Formatting.AQUA))
			.action((screen, option) -> {})
			.build();
	}

	private static ButtonOption levelOption(int level) {
		GexpressConfig.LevelRoadmapEntry reward = GexpressConfig.getLevelRoadmapEntry(level);
		int totalXp = LevelComponent.totalXpForLevel(level);
		int nextXp = LevelComponent.xpNeededForLevel(level);
		Text name = level <= 1
			? Text.translatable("gui.gexpress.config.option.xp_roadmap.level_start")
			: Text.translatable("gui.gexpress.config.option.xp_roadmap.level", level, totalXp);
		Text description = reward.configured()
			? Text.translatable("gui.gexpress.config.option.xp_roadmap.level.tooltip",
				nextXp, reward.title(), reward.description())
			: Text.translatable("gui.gexpress.config.option.xp_roadmap.level.no_reward.tooltip", nextXp);
		Text button = reward.configured()
			? Text.literal(reward.title()).formatted(Formatting.GOLD)
			: Text.translatable("gui.gexpress.config.option.xp_roadmap.no_reward").formatted(Formatting.DARK_GRAY);
		return ButtonOption.createBuilder()
			.name(name)
			.description(OptionDescription.of(description))
			.text(button)
			.action((screen, option) -> {})
			.build();
	}
}
