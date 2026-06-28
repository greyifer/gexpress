package dev.mapselect.client.screen;

import cat.rezelyn.watheextended.client.screen.GuidebookScreen;
import com.google.common.collect.ImmutableList;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.CustomTabProvider;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.tab.TabExt;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.permissions.GexpressPermissions;
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
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings({"deprecation", "unused"})
public final class GexpressDevCategory {
	private GexpressDevCategory() {}

	public static ConfigCategory build(Screen parent) {
		return new DevCategory();
	}

	private static final class DevCategory implements ConfigCategory, CustomTabProvider {
		private final Text name = Text.translatable("gui.gexpress.config.category.dev");
		private final Text tooltip = Text.translatable("gui.gexpress.config.category.dev.tooltip");

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
			return new DevTab(screen, tabArea, tooltip);
		}
	}

	private static final class DevTab implements TabExt {
		private final DevPanelWidget panel;
		private final ButtonWidget doneButton;
		private final Tooltip tooltip;

		private DevTab(YACLScreen screen, ScreenRect tabArea, Text tooltipText) {
			this.panel = new DevPanelWidget(tabArea.getLeft(), tabArea.getTop(), tabArea.width(), tabArea.height(), screen);
			this.doneButton = ButtonWidget.builder(ScreenTexts.DONE, button -> screen.finishOrSave())
				.size(Math.max(90, screen.width / 6), 20)
				.build();
			this.tooltip = Tooltip.of(tooltipText);
			refreshGrid(tabArea);
		}

		@Override
		public Text getTitle() {
			return Text.translatable("gui.gexpress.config.category.dev");
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

	private static final class DevPanelWidget extends ClickableWidget {
		private static final int BUTTON_W = 64;
		private static final int SMALL_BUTTON_W = 24;
		private static final int ROW_H = 24;
		private static final int CARD_GAP = 10;
		private final List<DevAction> actions = new ArrayList<>();
		private final Screen parent;
		private int scroll;
		private int maxScroll;

		private DevPanelWidget(int x, int y, int width, int height, Screen parent) {
			super(x, y, width, height, Text.translatable("gui.gexpress.config.category.dev"));
			this.parent = parent;
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.textRenderer == null) return;
			actions.clear();
			TextRenderer tr = client.textRenderer;
			int x = getX() + 14;
			int w = width - 28;
			int contentTop = getY() + 8;
			int contentBottom = getY() + height - 8;
			int y = contentTop - scroll;
			context.enableScissor(getX(), contentTop, getX() + width, contentBottom);
			y = drawToolsCard(context, tr, x, y, w, mouseX, mouseY) + CARD_GAP;
			if (w >= 760) {
				int leftW = (w - CARD_GAP) / 2;
				int rightW = w - leftW - CARD_GAP;
				int leftX = x;
				int rightX = x + leftW + CARD_GAP;
				int leftY = y;
				int rightY = y;
				leftY = drawXpCard(context, tr, leftX, leftY, leftW, mouseX, mouseY) + CARD_GAP;
				leftY = drawEconomyCard(context, tr, leftX, leftY, leftW, mouseX, mouseY) + CARD_GAP;
				leftY = drawVisualsCard(context, tr, leftX, leftY, leftW, mouseX, mouseY) + CARD_GAP;
				rightY = drawLevelRewardsCard(context, tr, rightX, rightY, rightW, mouseX, mouseY) + CARD_GAP;
				rightY = drawTagsCard(context, tr, rightX, rightY, rightW, mouseX, mouseY) + CARD_GAP;
				rightY = drawMapToolsCard(context, tr, rightX, rightY, rightW, mouseX, mouseY) + CARD_GAP;
				y = Math.max(leftY, rightY);
			} else {
				y = drawXpCard(context, tr, x, y, w, mouseX, mouseY) + CARD_GAP;
				y = drawEconomyCard(context, tr, x, y, w, mouseX, mouseY) + CARD_GAP;
				y = drawLevelRewardsCard(context, tr, x, y, w, mouseX, mouseY) + CARD_GAP;
				y = drawTagsCard(context, tr, x, y, w, mouseX, mouseY) + CARD_GAP;
				y = drawMapToolsCard(context, tr, x, y, w, mouseX, mouseY) + CARD_GAP;
				y = drawVisualsCard(context, tr, x, y, w, mouseX, mouseY) + CARD_GAP;
			}
			context.disableScissor();
			int contentHeight = y + scroll - contentTop;
			maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
			scroll = Math.max(0, Math.min(scroll, maxScroll));
			if (maxScroll > 0) {
				int trackX = getX() + width - 7;
				int trackH = contentBottom - contentTop;
				int thumbH = Math.max(24, trackH * trackH / Math.max(trackH, contentHeight));
				int thumbY = contentTop + Math.round((trackH - thumbH) * (scroll / (float) maxScroll));
				context.fill(trackX, contentTop, trackX + 2, contentBottom, 0x553F4A55);
				context.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xBBD9E3EE);
			}
		}

		private int drawToolsCard(DrawContext context, TextRenderer tr, int x, int y, int w, int mouseX, int mouseY) {
			ToolTile[] tiles = {
				new ToolTile("Model Placement", "Preview and position C4 or spy models.", 0xFF6FD7FF,
					() -> MinecraftClient.getInstance().setScreen(new GexpressModelPlacementScreen(parent))),
				new ToolTile("Tags & Permissions", "Edit tags, owner access, and level tags.", 0xFFF2C94C,
					() -> MinecraftClient.getInstance().setScreen(new GexpressTagEditorScreen(parent))),
				new ToolTile("Level Rewards", "Build claimable XP Roadmap rewards.", 0xFF7CC9A2,
					() -> MinecraftClient.getInstance().setScreen(new GexpressLevelRewardEditorScreen(parent))),
				new ToolTile("Case Editor", "Tune G'Express case prices and weighted skin drops.", 0xFFE0B65A,
					() -> MinecraftClient.getInstance().setScreen(new GexpressCaseEditorScreen(parent))),
				new ToolTile("Import Skins", "Open the skin folder and apply models without a loading screen.", 0xFFFF8FCB,
					() -> MinecraftClient.getInstance().setScreen(new GexpressSkinImportScreen(parent))),
				new ToolTile("Skin Model Editor", "Rename imported skins and tune their display transforms.", 0xFFFFA6D5,
					() -> MinecraftClient.getInstance().setScreen(new GexpressSkinEditorScreen(parent))),
				new ToolTile("Tutorial Room Editor", "Build, place, save, and test the 3D tutorial room.", 0xFF9AE66E,
					() -> MinecraftClient.getInstance().setScreen(new GexpressTutorialEditorScreen(parent))),
				new ToolTile("Copy Model Defaults", "Copy current model values for code defaults.", 0xFFB8A7FF,
					GexpressDevCategory::copyModelDefaultsToClipboard)
			};
			int columns = w >= 1120 ? 5 : w >= 760 ? 3 : 2;
			int tileGap = 8;
			int tileW = Math.max(112, (w - 24 - tileGap * (columns - 1)) / columns);
			int tileH = 50;
			int rows = (tiles.length + columns - 1) / columns;
			int h = 42 + rows * tileH + Math.max(0, rows - 1) * tileGap + 12;
			drawCard(context, tr, "Dev Workspace", "Fast access to the editors and live tuning tools.", x, y, w, h);
			int tileY = y + 38;
			for (int i = 0; i < tiles.length; i++) {
				int col = i % columns;
				int row = i / columns;
				int tileX = x + 12 + col * (tileW + tileGap);
				int currentTileW = col == columns - 1 ? x + w - 12 - tileX : tileW;
				drawToolTile(context, tr, tiles[i], tileX, tileY + row * (tileH + tileGap), currentTileW, tileH,
					mouseX, mouseY);
			}
			return y + h;
		}

		private void drawToolTile(DrawContext context, TextRenderer tr, ToolTile tile, int x, int y, int w, int h,
				int mouseX, int mouseY) {
			boolean hovered = contains(mouseX, mouseY, x, y, w, h);
			context.fill(x, y, x + w, y + h, hovered ? 0xBB263746 : 0x99202A36);
			context.drawBorder(x, y, w, h, hovered ? 0xFFE6F2FF : 0x887C8CA0);
			context.fill(x, y, x + 3, y + h, tile.accent());
			context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(tile.title(), w - 16)).formatted(Formatting.BOLD),
				x + 10, y + 9, 0xFFFFFFFF);
			context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(tile.subtitle(), w - 16)).formatted(Formatting.GRAY),
				x + 10, y + 25, 0xFFB6C1CC);
			actions.add(new DevAction(x, y, w, h, tile.action()));
		}

		private int drawXpCard(DrawContext context, TextRenderer tr, int x, int y, int w, int mouseX, int mouseY) {
			int h = 228;
			drawCard(context, tr, "XP Tuning", "Round XP, win bonuses, and level curve.", x, y, w, h);
			int rowY = y + 34;
			rowY = numberRow(context, tr, "Round Played", GexpressConfig.getLevelRoundXp(), x + 12, rowY, w - 24,
				1, 10, v -> GexpressConfig.levelRoundXp = v, GexpressConfig.LEVEL_XP_AMOUNT_MIN, GexpressConfig.LEVEL_XP_AMOUNT_MAX, mouseX, mouseY);
			rowY = numberRow(context, tr, "Winning Side", GexpressConfig.getLevelWinXp(), x + 12, rowY, w - 24,
				1, 10, v -> GexpressConfig.levelWinXp = v, GexpressConfig.LEVEL_XP_AMOUNT_MIN, GexpressConfig.LEVEL_XP_AMOUNT_MAX, mouseX, mouseY);
			rowY = numberRow(context, tr, "Neutral Win Bonus", GexpressConfig.getLevelNeutralWinBonusXp(), x + 12, rowY, w - 24,
				1, 10, v -> GexpressConfig.levelNeutralWinBonusXp = v, GexpressConfig.LEVEL_XP_AMOUNT_MIN, GexpressConfig.LEVEL_XP_AMOUNT_MAX, mouseX, mouseY);
			rowY = numberRow(context, tr, "Kill", GexpressConfig.getLevelKillXp(), x + 12, rowY, w - 24,
				1, 10, v -> GexpressConfig.levelKillXp = v, GexpressConfig.LEVEL_XP_AMOUNT_MIN, GexpressConfig.LEVEL_XP_AMOUNT_MAX, mouseX, mouseY);
			rowY = numberRow(context, tr, "Civilian Task", GexpressConfig.getLevelCivilianTaskXp(), x + 12, rowY, w - 24,
				1, 10, v -> GexpressConfig.levelCivilianTaskXp = v, GexpressConfig.LEVEL_XP_AMOUNT_MIN, GexpressConfig.LEVEL_XP_AMOUNT_MAX, mouseX, mouseY);
			rowY = numberRow(context, tr, "Base Level XP", GexpressConfig.getLevelBaseXp(), x + 12, rowY, w - 24,
				25, 100, v -> GexpressConfig.levelBaseXp = v, GexpressConfig.LEVEL_XP_REQUIRED_MIN, GexpressConfig.LEVEL_XP_REQUIRED_MAX, mouseX, mouseY);
			numberRow(context, tr, "XP Increase", GexpressConfig.getLevelXpIncrease(), x + 12, rowY, w - 24,
				25, 100, v -> GexpressConfig.levelXpIncrease = v, 0, GexpressConfig.LEVEL_XP_REQUIRED_MAX, mouseX, mouseY);
			return y + h;
		}

		private int drawEconomyCard(DrawContext context, TextRenderer tr, int x, int y, int w, int mouseX, int mouseY) {
			int h = 132;
			drawCard(context, tr, "Economy", "Prices used by paid interactables and shop-style tools.", x, y, w, h);
			int rowY = y + 34;
			rowY = numberRow(context, tr, "Golden Food Platter", GexpressConfig.getGoldFoodPlatterPrice(), x + 12, rowY, w - 24,
				5, 25, v -> GexpressConfig.goldFoodPlatterPrice = v, GexpressConfig.GRENADE_PRICE_MIN, GexpressConfig.GRENADE_PRICE_MAX, mouseX, mouseY);
			rowY = numberRow(context, tr, "Golden Drink Tray", GexpressConfig.getGoldDrinkTrayPrice(), x + 12, rowY, w - 24,
				5, 25, v -> GexpressConfig.goldDrinkTrayPrice = v, GexpressConfig.GRENADE_PRICE_MIN, GexpressConfig.GRENADE_PRICE_MAX, mouseX, mouseY);
			numberRow(context, tr, "Muted Note", GexpressConfig.getMutedNotePrice(), x + 12, rowY, w - 24,
				1, 10, v -> GexpressConfig.mutedNotePrice = v, GexpressConfig.GRENADE_PRICE_MIN, GexpressConfig.GRENADE_PRICE_MAX, mouseX, mouseY);
			return y + h;
		}

		private int drawLevelRewardsCard(DrawContext context, TextRenderer tr, int x, int y, int w, int mouseX, int mouseY) {
			int h = 154;
			drawCard(context, tr, "Level Rewards", "Configured XP Roadmap rewards.", x, y, w, h);
			drawButton(context, tr, "Edit Rewards", x + w - 106, y + 10, 92, mouseX, mouseY,
				() -> MinecraftClient.getInstance().setScreen(new GexpressLevelRewardEditorScreen(parent)));
			List<GexpressConfig.LevelRoadmapEntry> rewards = new ArrayList<>(GexpressConfig.getLevelRoadmapEntries());
			rewards.sort((a, b) -> Integer.compare(a.level(), b.level()));
			int rowY = y + 38;
			if (rewards.isEmpty()) {
				context.drawTextWithShadow(tr, Text.literal("No rewards configured yet.").formatted(Formatting.GRAY),
					x + 14, rowY, 0xFF9EACB9);
			} else {
				for (int i = 0; i < Math.min(4, rewards.size()); i++) {
					GexpressConfig.LevelRoadmapEntry reward = rewards.get(i);
					String title = reward.rewardTitles().isEmpty() ? reward.title() : String.join(" + ", reward.rewardTitles());
					context.fill(x + 12, rowY, x + w - 12, rowY + 19, 0x33242E38);
					context.drawTextWithShadow(tr, Text.literal("LvL " + reward.level()).formatted(Formatting.GOLD),
						x + 20, rowY + 5, 0xFFFFD57A);
					context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(title, w - 160)),
						x + 78, rowY + 5, 0xFFFFFFFF);
					String commands = reward.commands().size() + " command" + (reward.commands().size() == 1 ? "" : "s");
					context.drawTextWithShadow(tr, Text.literal(commands).formatted(Formatting.GRAY),
						x + w - 20 - tr.getWidth(commands), rowY + 5, 0xFF9EACB9);
					rowY += 22;
				}
				if (rewards.size() > 4) {
					context.drawTextWithShadow(tr, Text.literal("+" + (rewards.size() - 4) + " more").formatted(Formatting.GRAY),
						x + 20, rowY + 4, 0xFF9EACB9);
				}
			}
			return y + h;
		}

		private int drawTagsCard(DrawContext context, TextRenderer tr, int x, int y, int w, int mouseX, int mouseY) {
			int h = 134;
			drawCard(context, tr, "Tags", "Player permissions and level-tag rules are summarized here.", x, y, w, h);
			drawButton(context, tr, "Edit Tags", x + w - 88, y + 10, 74, mouseX, mouseY,
				() -> MinecraftClient.getInstance().setScreen(new GexpressTagEditorScreen(parent)));
			List<GexpressConfig.LevelTagEntry> levelTags = GexpressConfig.getLevelTagEntries();
			int rowY = y + 38;
			context.drawTextWithShadow(tr, Text.literal("Level tags").formatted(Formatting.GRAY),
				x + 18, rowY, 0xFF9EACB9);
			rowY += 14;
			if (levelTags.isEmpty()) {
				context.drawTextWithShadow(tr, Text.literal("No level tags configured.").formatted(Formatting.DARK_GRAY),
					x + 18, rowY, 0xFF777777);
			} else {
				int drawX = x + 18;
				for (int i = 0; i < Math.min(5, levelTags.size()); i++) {
					GexpressConfig.LevelTagEntry tag = levelTags.get(i);
					String label = "LvL " + tag.level() + " " + tag.displayName();
					int tw = Math.min(112, tr.getWidth(label) + 12);
					context.fill(drawX, rowY, drawX + tw, rowY + 18, 0x66000000 | (tag.color() & 0xFFFFFF));
					context.drawBorder(drawX, rowY, tw, 18, 0xAAFFFFFF);
					context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(label, tw - 8)),
						drawX + 5, rowY + 5, 0xFFFFFFFF);
					drawX += tw + 6;
					if (drawX > x + w - 120) break;
				}
			}
			context.drawTextWithShadow(tr,
				Text.literal(GexpressPermissions.permissionKeys().size() + " permission flags available").formatted(Formatting.GRAY),
				x + 18, y + h - 24, 0xFF9EACB9);
			return y + h;
		}

		private int drawMapToolsCard(DrawContext context, TextRenderer tr, int x, int y, int w, int mouseX, int mouseY) {
			int h = 122;
			drawCard(context, tr, "Map Tools", "Grenade trace blocks and Painter doorway test helpers.", x, y, w, h);
			int buttonX = x + 12;
			int buttonY = y + 40;
			context.drawTextWithShadow(tr, Text.literal("Grenade trace blocks").formatted(Formatting.GRAY),
				buttonX, buttonY - 12, 0xFF9EACB9);
			drawButton(context, tr, "Add Looked Block", buttonX, buttonY, 126, mouseX, mouseY,
				() -> addLookedBlockToGrenadePassThrough(MinecraftClient.getInstance()));
			drawButton(context, tr, "Clear List", buttonX + 134, buttonY, 82, mouseX, mouseY,
				() -> {
					GexpressConfig.setGrenadeLineOfSightPassThroughBlockStrings(List.of());
					GexpressOptionsScreen.pushGexpressConfigToServer();
				});
			String count = GexpressConfig.getGrenadeLineOfSightPassThroughBlockStrings().size() + " configured";
			context.drawTextWithShadow(tr, Text.literal(count).formatted(Formatting.GRAY), x + w - 18 - tr.getWidth(count),
				buttonY + 5, 0xFF9EACB9);
			int doorwayY = buttonY + 40;
			context.drawTextWithShadow(tr, Text.literal("Painter doorways").formatted(Formatting.GRAY),
				buttonX, doorwayY - 12, 0xFF9EACB9);
			drawButton(context, tr, "Toggle Looked Door", buttonX, doorwayY, 126, mouseX, mouseY,
				() -> sendCommand("g admin painterdoor toggle"));
			drawButton(context, tr, "Clear Disabled", buttonX + 134, doorwayY, 106, mouseX, mouseY,
				() -> sendCommand("g admin painterdoor clear"));
			return y + h;
		}

		private int drawVisualsCard(DrawContext context, TextRenderer tr, int x, int y, int w, int mouseX, int mouseY) {
			int h = 166;
			drawCard(context, tr, "Visuals", "Fine tuning for overlays and screen effects.", x, y, w, h);
			int rowY = y + 34;
			rowY = numberRow(context, tr, "Short Sight Range", Math.round(GexpressConfig.getShortSightedEntityRange()), x + 12, rowY, w - 24,
				1, 5, v -> GexpressConfig.shortSightedFogRange = v, Math.round(GexpressConfig.SHORT_SIGHTED_ENTITY_RANGE_MIN),
				Math.round(GexpressConfig.SHORT_SIGHTED_ENTITY_RANGE_MAX), mouseX, mouseY);
			rowY = numberRow(context, tr, "Medic Block Flash", GexpressConfig.getMedicShieldBlockFlashAlpha(), x + 12, rowY, w - 24,
				2, 10, v -> GexpressConfig.medicShieldBlockFlashAlpha = v, GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MIN,
				GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MAX, mouseX, mouseY);
			rowY = numberRow(context, tr, "Medic Break Flash", GexpressConfig.getMedicShieldBreakFlashAlpha(), x + 12, rowY, w - 24,
				2, 10, v -> GexpressConfig.medicShieldBreakFlashAlpha = v, GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MIN,
				GexpressConfig.MEDIC_SHIELD_FLASH_ALPHA_MAX, mouseX, mouseY);
			numberRow(context, tr, "Silent Shadow %", Math.round(GexpressConfig.getSilentShadowAlpha() * 100.0F), x + 12, rowY, w - 24,
				1, 5, v -> GexpressConfig.silentShadowAlpha = v / 100.0F, Math.round(GexpressConfig.SILENT_SHADOW_ALPHA_MIN * 100.0F),
				Math.round(GexpressConfig.SILENT_SHADOW_ALPHA_MAX * 100.0F), mouseX, mouseY);
			return y + h;
		}

		private void drawCard(DrawContext context, TextRenderer tr, String title, String subtitle, int x, int y, int w, int h) {
			context.fill(x, y, x + w, y + h, 0x77202630);
			context.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x66303A46);
			context.drawBorder(x, y, w, h, 0xAA718499);
			context.fill(x, y, x + 4, y + h, 0xFF7CC9A2);
			context.drawTextWithShadow(tr, Text.literal(title).formatted(Formatting.BOLD), x + 14, y + 10, 0xFFFFFFFF);
			context.drawTextWithShadow(tr, Text.literal(subtitle).formatted(Formatting.GRAY), x + 14, y + 22, 0xFF9EACB9);
		}

		private int numberRow(DrawContext context, TextRenderer tr, String label, int value, int x, int y, int w,
				int step, int bigStep, IntConsumer setter, int min, int max, int mouseX, int mouseY) {
			context.fill(x, y, x + w, y + ROW_H - 3, 0x33242E38);
			context.drawTextWithShadow(tr, Text.literal(label), x + 8, y + 6, 0xFFE8EEF5);
			String valueText = Integer.toString(value);
			int valueX = x + w - BUTTON_W - SMALL_BUTTON_W * 2 - 24;
			context.drawTextWithShadow(tr, Text.literal(valueText).formatted(Formatting.AQUA),
				valueX + BUTTON_W - tr.getWidth(valueText), y + 6, 0xFF7CE9F2);
			drawSmallButton(context, tr, "-", x + w - SMALL_BUTTON_W * 2 - 8, y + 2, mouseX, mouseY,
				() -> applyInt(setter, value - (hasShift() ? bigStep : step), min, max));
			drawSmallButton(context, tr, "+", x + w - SMALL_BUTTON_W - 4, y + 2, mouseX, mouseY,
				() -> applyInt(setter, value + (hasShift() ? bigStep : step), min, max));
			return y + ROW_H;
		}

		private void drawSmallButton(DrawContext context, TextRenderer tr, String label, int x, int y, int mouseX,
				int mouseY, Runnable action) {
			drawButton(context, tr, label, x, y, SMALL_BUTTON_W, mouseX, mouseY, action);
		}

		private void drawButton(DrawContext context, TextRenderer tr, String label, int x, int y, int w, int mouseX,
				int mouseY, Runnable action) {
			int h = 18;
			boolean hovered = contains(mouseX, mouseY, x, y, w, h);
			context.fill(x, y, x + w, y + h, hovered ? 0xAA3A4A5B : 0x77303A45);
			context.drawBorder(x, y, w, h, hovered ? 0xFFE8F2FF : 0xAA8795A5);
			String trimmed = tr.trimToWidth(label, w - 8);
			context.drawCenteredTextWithShadow(tr, Text.literal(trimmed), x + w / 2, y + 5, 0xFFFFFFFF);
			actions.add(new DevAction(x, y, w, h, action));
		}

		private void applyInt(IntConsumer setter, int value, int min, int max) {
			setter.accept(Math.max(min, Math.min(max, value)));
			GexpressOptionsScreen.pushGexpressConfigToServer();
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 0) {
				for (DevAction action : actions) {
					if (!action.contains(mouseX, mouseY)) continue;
					action.action().run();
					return true;
				}
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
			if (maxScroll <= 0 || mouseX < getX() || mouseX >= getX() + width || mouseY < getY() || mouseY >= getY() + height) {
				return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
			}
			scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(verticalAmount * 24.0D)));
			return true;
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		}

		private static boolean contains(double mouseX, double mouseY, int x, int y, int w, int h) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}

		private static boolean hasShift() {
			return Screen.hasShiftDown();
		}

		private record DevAction(int x, int y, int w, int h, Runnable action) {
			private boolean contains(double mouseX, double mouseY) {
				return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
			}
		}

		private record ToolTile(String title, String subtitle, int accent, Runnable action) {
		}
	}

	private static void addLookedBlockToGrenadePassThrough(MinecraftClient client) {
		if (client == null || client.world == null || !(client.crosshairTarget instanceof BlockHitResult hit)
				|| hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		String id = Registries.BLOCK.getId(client.world.getBlockState(hit.getBlockPos()).getBlock()).toString();
		List<String> rows = new ArrayList<>(GexpressConfig.getGrenadeLineOfSightPassThroughBlockStrings());
		if (!rows.contains(id)) rows.add(id);
		GexpressConfig.setGrenadeLineOfSightPassThroughBlockStrings(rows);
		GexpressOptionsScreen.pushGexpressConfigToServer();
		if (client.player != null) {
			client.player.sendMessage(Text.literal("Added " + id + " to grenade pass-through blocks.")
				.formatted(Formatting.GREEN), true);
		}
	}

	private static void sendCommand(String command) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null && client.player.networkHandler != null) {
			client.player.networkHandler.sendChatCommand(command);
		}
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

	private static OptionGroup levelRewardEditorGroup() {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.dev.level_rewards"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.dev.level_rewards.tooltip")))
			.collapsed(false)
			.option(ButtonOption.createBuilder()
				.name(Text.translatable("gui.gexpress.config.option.dev.level_reward_editor"))
				.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.dev.level_reward_editor.tooltip")))
				.text(Text.translatable("gui.gexpress.config.option.dev.level_reward_editor.open"))
				.action((screen, option) -> MinecraftClient.getInstance()
					.setScreen(new GexpressLevelRewardEditorScreen(screen)))
				.build())
			.build();
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
