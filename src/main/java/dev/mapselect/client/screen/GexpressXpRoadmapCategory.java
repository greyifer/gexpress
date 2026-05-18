package dev.mapselect.client.screen;

import com.google.common.collect.ImmutableList;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.CustomTabProvider;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.tab.TabExt;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.level.LevelComponent;
import dev.mapselect.network.ClaimLevelRewardPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tab.Tab;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class GexpressXpRoadmapCategory {
	private GexpressXpRoadmapCategory() {}

	public static ConfigCategory build(Screen parent) {
		return new XpRoadmapCategory();
	}

	private static final class XpRoadmapCategory implements ConfigCategory, CustomTabProvider {
		private final Text name = Text.translatable("gui.gexpress.config.category.xp_roadmap");
		private final Text tooltip = Text.translatable("gui.gexpress.config.category.xp_roadmap.tooltip");

		@Override
		public @NotNull Text name() {
			return name;
		}

		@Override
		public @NotNull ImmutableList<OptionGroup> groups() {
			return ImmutableList.of();
		}

		@Override
		public @NotNull Text tooltip() {
			return tooltip;
		}

		@Override
		public Tab createTab(YACLScreen screen, ScreenRect tabArea) {
			return new XpRoadmapTab(screen, tabArea, tooltip);
		}
	}

	private static final class XpRoadmapTab implements TabExt {
		private final XpRoadmapWidget panel;
		private final ButtonWidget doneButton;
		private final Tooltip tooltip;

		private XpRoadmapTab(YACLScreen screen, ScreenRect tabArea, Text tooltipText) {
			this.panel = new XpRoadmapWidget(tabArea.getLeft(), tabArea.getTop(), tabArea.width(), tabArea.height());
			this.doneButton = ButtonWidget.builder(ScreenTexts.DONE, button -> screen.finishOrSave())
				.size(Math.max(90, screen.width / 6), 20)
				.build();
			this.tooltip = Tooltip.of(tooltipText);
			refreshGrid(tabArea);
		}

		@Override
		public Text getTitle() {
			return Text.translatable("gui.gexpress.config.category.xp_roadmap");
		}

		@Override
		public void forEachChild(Consumer<ClickableWidget> consumer) {
			consumer.accept(panel);
			consumer.accept(doneButton);
		}

		@Override
		public void refreshGrid(ScreenRect tabArea) {
			panel.setDimensionsAndPosition(tabArea.width(), tabArea.height() - 30, tabArea.getLeft(), tabArea.getTop());
			doneButton.setDimensionsAndPosition(Math.max(90, tabArea.width() / 5), 20,
				tabArea.getLeft() + tabArea.width() - Math.max(90, tabArea.width() / 5) - 12,
				tabArea.getBottom() - 24);
		}

		@Override
		public @Nullable Tooltip getTooltip() {
			return tooltip;
		}
	}

	private static final class XpRoadmapWidget extends ClickableWidget {
		private static final int CARD_HEIGHT = 82;
		private static final int CARD_GAP = 8;
		private final List<LevelCard> cards = new ArrayList<>();
		private int selectedLevel;
		private int scroll;
		private int maxScroll;
		private int gridX;
		private int gridY;
		private int gridW;
		private int gridH;
		private int claimX;
		private int claimY;
		private int claimW;
		private int claimH;
		private int claimLevel;

		private XpRoadmapWidget(int x, int y, int width, int height) {
			super(x, y, width, height, Text.translatable("gui.gexpress.config.category.xp_roadmap"));
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.textRenderer == null) return;
			PlayerXp snapshot = playerXp(client);
			int maxLevel = Math.max(GexpressConfig.getLevelRoadmapDisplayLevels(), snapshot.level() + 3);
			if (selectedLevel < 1 || selectedLevel > maxLevel) selectedLevel = Math.min(snapshot.level(), maxLevel);
			cards.clear();

			int headerX = getX() + 14;
			int headerY = getY() + 14;
			int headerW = width - 28;
			drawHeader(context, client.textRenderer, snapshot, headerX, headerY, headerW);

			int contentTop = getY() + 102;
			int contentBottom = getY() + height - 8;
			boolean split = width >= 560;
			int detailW = split ? Math.min(288, Math.max(220, width / 3)) : 0;
			int detailH = split ? contentBottom - contentTop : 86;
			int detailX = split ? getX() + width - detailW - 14 : headerX;
			int detailY = contentTop;
			int listX = headerX;
			int listW = split ? detailX - listX - 14 : headerW;
			int listY = split ? contentTop : contentTop + detailH + 10;
			int listH = Math.max(0, contentBottom - listY);

			drawDetail(context, client.textRenderer, snapshot, selectedLevel, detailX, detailY,
				split ? detailW : headerW, detailH, mouseX, mouseY);
			drawCards(context, client.textRenderer, snapshot, maxLevel, listX, listY, listW, listH, mouseX, mouseY);
		}

		private void drawHeader(DrawContext context, TextRenderer textRenderer, PlayerXp snapshot, int x, int y, int w) {
			context.fill(x, y, x + w, y + 72, 0x55202630);
			context.drawBorder(x, y, w, 72, 0x88AAB8C8);
			context.drawTextWithShadow(textRenderer,
				Text.translatable("gui.gexpress.config.xp_roadmap.level", snapshot.level())
					.formatted(Formatting.BOLD),
				x + 14, y + 12, 0xFFFFFFFF);
			context.drawTextWithShadow(textRenderer,
				Text.translatable("gui.gexpress.config.xp_roadmap.progress",
					snapshot.levelXp(), snapshot.neededXp()),
				x + 14, y + 28, 0xFFB8C6D4);
			Text totalText = Text.translatable("gui.gexpress.config.xp_roadmap.total_xp", snapshot.totalXp());
			int totalWidth = textRenderer.getWidth(totalText);
			if (totalWidth < w - 150) {
				context.drawTextWithShadow(textRenderer, totalText, x + w - totalWidth - 14, y + 12, 0xFF8FE6D0);
			}
			int barX = x + 14;
			int barY = y + 52;
			int barW = w - 28;
			float progress = snapshot.neededXp() <= 0 ? 0.0F
				: Math.min(1.0F, snapshot.levelXp() / (float) snapshot.neededXp());
			context.fill(barX, barY, barX + barW, barY + 7, 0xCC0C1118);
			context.fill(barX, barY, barX + Math.round(barW * progress), barY + 7, 0xFF7CC9A2);
			context.drawBorder(barX, barY, barW, 7, 0x885E7184);
		}

		private void drawDetail(DrawContext context, TextRenderer textRenderer, PlayerXp snapshot, int level,
				int x, int y, int w, int h, int mouseX, int mouseY) {
			GexpressConfig.LevelRoadmapEntry reward = GexpressConfig.getLevelRoadmapEntry(level);
			int totalXp = LevelComponent.totalXpForLevel(level);
			int nextXp = LevelComponent.xpNeededForLevel(level);
			Status status = status(level, snapshot.level());
			claimLevel = 0;
			context.fill(x, y, x + w, y + h, 0x44202630);
			context.drawBorder(x, y, w, h, status.borderColor());
			context.fill(x, y, x + 4, y + h, status.accentColor());
			context.drawTextWithShadow(textRenderer,
				Text.translatable("gui.gexpress.config.xp_roadmap.level", level).formatted(Formatting.BOLD),
				x + 14, y + 12, 0xFFFFFFFF);
			context.drawTextWithShadow(textRenderer, status.text(), x + w - textRenderer.getWidth(status.text()) - 12,
				y + 12, status.textColor());
			int lineY = y + 31;
			context.drawTextWithShadow(textRenderer,
				Text.translatable("gui.gexpress.config.xp_roadmap.total_xp", totalXp),
				x + 14, lineY, 0xFFB8C6D4);
			lineY += 13;
			context.drawTextWithShadow(textRenderer,
				Text.translatable("gui.gexpress.config.xp_roadmap.next_xp", nextXp),
				x + 14, lineY, 0xFFB8C6D4);
			if (level > snapshot.level()) {
				lineY += 13;
				context.drawTextWithShadow(textRenderer,
					Text.translatable("gui.gexpress.config.xp_roadmap.need_total",
						Math.max(0, totalXp - snapshot.totalXp())),
					x + 14, lineY, 0xFF8FA1B2);
			}

			if (h < 128) {
				drawClaimButton(context, textRenderer, snapshot, reward, level, x + 14, y + h - 26, w - 28, mouseX, mouseY);
				return;
			}
			lineY += 24;
			context.drawTextWithShadow(textRenderer,
				Text.translatable("gui.gexpress.config.xp_roadmap.reward").formatted(Formatting.GRAY),
				x + 14, lineY, 0xFF9FB0BF);
			lineY += 14;
			String title = reward.configured()
				? reward.title()
				: Text.translatable("gui.gexpress.config.xp_roadmap.no_reward").getString();
			context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(title, w - 28)),
				x + 14, lineY, reward.configured() ? 0xFFFFD57A : 0xFF7D8792);
			lineY += 14;
			String description = reward.configured()
				? reward.description()
				: Text.translatable("gui.gexpress.config.xp_roadmap.no_reward_description").getString();
			List<OrderedText> lines = textRenderer.wrapLines(Text.literal(description), w - 28);
			int maxLines = Math.max(1, (y + h - lineY - 34) / 10);
			for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
				context.drawTextWithShadow(textRenderer, lines.get(i), x + 14, lineY + i * 10, 0xFFD7DEE6);
			}
			drawClaimButton(context, textRenderer, snapshot, reward, level, x + 14, y + h - 26, w - 28, mouseX, mouseY);
		}

		private void drawClaimButton(DrawContext context, TextRenderer textRenderer, PlayerXp snapshot,
				GexpressConfig.LevelRoadmapEntry reward, int level, int x, int y, int w, int mouseX, int mouseY) {
			if (!reward.configured() || level > snapshot.level()) return;
			boolean claimed = snapshot.claimedRewards().contains(level);
			claimX = x;
			claimY = y;
			claimW = w;
			claimH = 18;
			claimLevel = claimed ? 0 : level;
			boolean hovered = !claimed && contains(mouseX, mouseY, claimX, claimY, claimW, claimH);
			int fill = claimed ? 0x66343A42 : hovered ? 0xCC456D59 : 0xAA2F5D49;
			int border = claimed ? 0x88727C86 : hovered ? 0xFFE1FFF0 : 0xCC8ADDB8;
			Text label = claimed
				? Text.translatable("gui.gexpress.config.xp_roadmap.claimed")
				: Text.translatable("gui.gexpress.config.xp_roadmap.claim");
			context.fill(claimX, claimY, claimX + claimW, claimY + claimH, fill);
			context.drawBorder(claimX, claimY, claimW, claimH, border);
			context.drawTextWithShadow(textRenderer, label,
				claimX + claimW / 2 - textRenderer.getWidth(label) / 2, claimY + 5,
				claimed ? 0xFF9CA7B2 : 0xFFFFFFFF);
		}

		private void drawCards(DrawContext context, TextRenderer textRenderer, PlayerXp snapshot, int maxLevel,
				int x, int y, int w, int h, int mouseX, int mouseY) {
			gridX = x;
			gridY = y;
			gridW = w;
			gridH = h;
			if (h <= 0 || w <= 0) return;
			int columns = Math.max(1, w / 144);
			int cardW = Math.max(112, (w - (columns - 1) * CARD_GAP) / columns);
			int rows = (maxLevel + columns - 1) / columns;
			int contentH = rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
			maxScroll = Math.max(0, contentH - h);
			scroll = Math.max(0, Math.min(scroll, maxScroll));

			context.enableScissor(x, y, x + w, y + h);
			for (int level = 1; level <= maxLevel; level++) {
				int index = level - 1;
				int cardX = x + (index % columns) * (cardW + CARD_GAP);
				int cardY = y + (index / columns) * (CARD_HEIGHT + CARD_GAP) - scroll;
				if (cardY + CARD_HEIGHT >= y && cardY <= y + h) {
					drawCard(context, textRenderer, snapshot, level, cardX, cardY, cardW, mouseX, mouseY);
				}
			}
			context.disableScissor();

			if (maxScroll > 0) {
				int trackX = x + w - 4;
				int thumbH = Math.max(24, h * h / Math.max(h, contentH));
				int thumbY = y + Math.round((h - thumbH) * (scroll / (float) maxScroll));
				context.fill(trackX, y, trackX + 2, y + h, 0x553F4A55);
				context.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xBBD9E3EE);
			}
		}

		private void drawCard(DrawContext context, TextRenderer textRenderer, PlayerXp snapshot, int level,
				int x, int y, int w, int mouseX, int mouseY) {
			boolean selected = level == selectedLevel;
			boolean hovered = contains(mouseX, mouseY, x, y, w, CARD_HEIGHT);
			Status status = status(level, snapshot.level());
			context.fill(x, y, x + w, y + CARD_HEIGHT,
				selected ? 0xAA26394D : hovered ? 0x66323D49 : 0x44262E38);
			context.drawBorder(x, y, w, CARD_HEIGHT, selected ? 0xFFE8F2FF : hovered ? 0xCCB8C8D8 : status.borderColor());
			context.fill(x, y, x + 4, y + CARD_HEIGHT, status.accentColor());
			context.drawTextWithShadow(textRenderer,
				Text.translatable("gui.gexpress.config.xp_roadmap.level", level).formatted(Formatting.BOLD),
				x + 12, y + 9, 0xFFFFFFFF);
			context.drawTextWithShadow(textRenderer, status.text(), x + 12, y + 23, status.textColor());
			String total = level <= 1
				? Text.translatable("gui.gexpress.config.xp_roadmap.start").getString()
				: Text.translatable("gui.gexpress.config.xp_roadmap.total_xp",
					LevelComponent.totalXpForLevel(level)).getString();
			context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(total, w - 24)),
				x + 12, y + 39, 0xFFB8C6D4);
			GexpressConfig.LevelRoadmapEntry reward = GexpressConfig.getLevelRoadmapEntry(level);
			String rewardText = reward.configured()
				? reward.title()
				: Text.translatable("gui.gexpress.config.xp_roadmap.no_reward").getString();
			context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(rewardText, w - 24)),
				x + 12, y + 58, reward.configured() ? 0xFFFFD57A : 0xFF79838E);
			cards.add(new LevelCard(x, y, w, CARD_HEIGHT, level));
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			for (LevelCard card : cards) {
				if (!card.contains(mouseX, mouseY)) continue;
				selectedLevel = card.level();
				return true;
			}
			if (button == 0 && claimLevel > 0 && contains(mouseX, mouseY, claimX, claimY, claimW, claimH)
					&& ClientPlayNetworking.canSend(ClaimLevelRewardPayload.ID)) {
				ClientPlayNetworking.send(new ClaimLevelRewardPayload(claimLevel));
				return true;
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
			if (maxScroll <= 0 || mouseX < gridX || mouseX >= gridX + gridW || mouseY < gridY || mouseY >= gridY + gridH) {
				return false;
			}
			scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(verticalAmount * 24.0D)));
			return true;
		}

		private PlayerXp playerXp(MinecraftClient client) {
			if (client.player == null || client.world == null) {
				return new PlayerXp(1, 0, 0, LevelComponent.xpNeededForLevel(1), Set.of());
			}
			UUID playerId = client.player.getUuid();
			LevelComponent levels = LevelComponent.KEY.getNullable(client.world);
			if (levels == null) return new PlayerXp(1, 0, 0, LevelComponent.xpNeededForLevel(1), Set.of());
			return new PlayerXp(levels.level(playerId), levels.xp(playerId), levels.xpIntoLevel(playerId),
				levels.xpNeededForNextLevel(playerId), levels.claimedRewards(playerId));
		}

		private Status status(int level, int currentLevel) {
			if (level < currentLevel) {
				return new Status(Text.translatable("gui.gexpress.config.xp_roadmap.unlocked"),
					0xFF7CC9A2, 0xFF7CC9A2, 0xAA7CC9A2);
			}
			if (level == currentLevel) {
				return new Status(Text.translatable("gui.gexpress.config.xp_roadmap.current"),
					0xFFFFC857, 0xFFFFC857, 0xAAFFC857);
			}
			return new Status(Text.translatable("gui.gexpress.config.xp_roadmap.locked"),
				0xFF647282, 0xFF647282, 0x88647282);
		}

		private boolean contains(double mouseX, double mouseY, int x, int y, int w, int h) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	private record PlayerXp(int level, int totalXp, int levelXp, int neededXp, Set<Integer> claimedRewards) {}

	private record Status(Text text, int accentColor, int borderColor, int textColor) {}

	private record LevelCard(int x, int y, int width, int height, int level) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}
}
