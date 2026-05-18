package dev.mapselect.client.screen;

import com.google.common.collect.ImmutableList;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.CustomTabProvider;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.tab.TabExt;
import dev.mapselect.host.PlayerTag;
import dev.mapselect.host.PlayerTagComponent;
import dev.mapselect.level.LevelComponent;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tab.Tab;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class GexpressPlayersCategory {
	private GexpressPlayersCategory() {}

	public static ConfigCategory build(Screen parent) {
		return new PlayersCategory();
	}

	private static final class PlayersCategory implements ConfigCategory, CustomTabProvider {
		private final Text name = Text.translatable("gui.gexpress.config.category.players");
		private final Text tooltip = Text.translatable("gui.gexpress.config.category.players.tooltip");

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
			return new PlayersTab(screen, tabArea, tooltip);
		}
	}

	private static final class PlayersTab implements TabExt {
		private final PlayersPanelWidget panel;
		private final ButtonWidget doneButton;
		private final Tooltip tooltip;

		private PlayersTab(YACLScreen screen, ScreenRect tabArea, Text tooltipText) {
			this.panel = new PlayersPanelWidget(tabArea.getLeft(), tabArea.getTop(), tabArea.width(), tabArea.height());
			this.doneButton = ButtonWidget.builder(ScreenTexts.DONE, button -> screen.finishOrSave())
				.size(Math.max(90, screen.width / 6), 20)
				.build();
			this.tooltip = Tooltip.of(tooltipText);
			refreshGrid(tabArea);
		}

		@Override
		public Text getTitle() {
			return Text.translatable("gui.gexpress.config.category.players");
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

	private static final class PlayersPanelWidget extends ClickableWidget {
		private static final int ROW_HEIGHT = 38;
		private static final int HEAD_SIZE = 24;
		private static final int TAG_BUTTON_WIDTH = 76;
		private static final int TAG_BUTTON_HEIGHT = 17;
		private static final int TAG_BUTTON_GAP = 4;
		private static final int CHANGE_BUTTON_WIDTH = 92;
		private static final int LEVEL_EDITOR_HEIGHT = 58;
		private static final int LEVEL_FIELD_WIDTH = 62;
		private static final int LEVEL_APPLY_WIDTH = 38;
		private static final PlayerTag[] BUILTIN_TAGS = {
			PlayerTag.OWNER,
			PlayerTag.STAFF,
			PlayerTag.HOST,
			PlayerTag.CREATOR,
			PlayerTag.TRUSTED
		};

		private final List<TagButton> tagButtons = new ArrayList<>();
		private final List<ChangeButton> changeButtons = new ArrayList<>();
		private final List<LevelFieldBox> levelFields = new ArrayList<>();
		private final List<LevelApplyButton> levelApplyButtons = new ArrayList<>();
		private final Map<UUID, LevelDraft> levelDrafts = new HashMap<>();
		private FocusedLevelField focusedLevelField;
		private UUID expandedPlayer;
		private int scroll;

		private PlayersPanelWidget(int x, int y, int width, int height) {
			super(x, y, width, height, Text.translatable("gui.gexpress.config.category.players"));
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.textRenderer == null) return;

			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.translatable("gui.gexpress.config.category.players"),
				getX() + width / 2, getY() + 8, 0xFFFFFF);
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.translatable("gui.gexpress.config.players.subtitle").formatted(Formatting.GRAY),
				getX() + width / 2, getY() + 21, 0xA0A0A0);

			tagButtons.clear();
			changeButtons.clear();
			levelFields.clear();
			levelApplyButtons.clear();
			List<PlayerListEntry> entries = onlinePlayers();
			if (entries.isEmpty()) {
				scroll = 0;
				expandedPlayer = null;
				context.drawCenteredTextWithShadow(client.textRenderer,
					Text.translatable("gui.gexpress.config.players.empty").formatted(Formatting.DARK_GRAY),
					getX() + width / 2, getY() + height / 2, 0x777777);
				return;
			}

			int listX = getX() + 12;
			int listWidth = width - 24;
			int top = getY() + 44;
			int bottom = getY() + height - 6;
			scroll = clampScroll(scroll, entries, listWidth, bottom - top);
			int y = top - scroll;
			for (PlayerListEntry entry : entries) {
				int rowHeight = rowHeight(entry, listWidth);
				if (y + rowHeight >= top && y <= bottom) {
					drawPlayerRow(context, client, entry, listX, y, listWidth, rowHeight, mouseX, mouseY);
				}
				y += rowHeight;
			}
		}

		private void drawPlayerRow(DrawContext context, MinecraftClient client, PlayerListEntry entry, int x, int y,
				int rowWidth, int rowHeight, int mouseX, int mouseY) {
			UUID id = entry.getProfile().getId();
			String name = entry.getProfile().getName();
			List<GexpressPermissions.TagInfo> tags = currentTags(entry);
			boolean dev = tags.stream().anyMatch(tag -> PlayerTag.DEV.id().equals(tag.id()));
			boolean self = isSelf(client, entry);
			boolean locked = dev && !self;
			boolean expanded = id.equals(expandedPlayer) && !locked;
			boolean hovered = mouseY >= y && mouseY < y + Math.min(rowHeight, ROW_HEIGHT)
				&& mouseX >= x && mouseX < x + rowWidth;
			context.fill(x, y, x + rowWidth, y + rowHeight - 4, hovered || expanded ? 0x44222222 : 0x33111111);
			drawHead(context, entry, x + 6, y + 6);
			context.drawTextWithShadow(client.textRenderer, Text.literal(name), x + 38, y + 7, 0xFFFFFFFF);
			drawTagBadges(context, client, tags, x + 38, y + 21);

			if (locked) {
				context.drawTextWithShadow(client.textRenderer,
					Text.translatable("gui.gexpress.config.players.locked").formatted(Formatting.DARK_GRAY),
					x + rowWidth - 78, y + 14, 0xFF777777);
				return;
			}

			drawChangeButton(context, client, id, x + rowWidth - CHANGE_BUTTON_WIDTH - 6, y + 10, mouseX, mouseY);
			if (expanded) {
				int detailX = x + 38;
				int detailWidth = rowWidth - 44;
				drawLevelEditor(context, client, id, name, detailX, y + ROW_HEIGHT - 2, detailWidth, mouseX, mouseY);
				drawTagDropdown(context, client, id, name, tags, detailX, y + ROW_HEIGHT + LEVEL_EDITOR_HEIGHT - 4,
					detailWidth, mouseX, mouseY);
			}
		}

		private void drawTagBadges(DrawContext context, MinecraftClient client, List<GexpressPermissions.TagInfo> tags,
				int x, int y) {
			int drawX = x;
			int shown = 0;
			for (GexpressPermissions.TagInfo tag : tags) {
				if (shown >= 2) break;
				Text badge = GexpressPermissions.tagBadge(tag);
				context.drawTextWithShadow(client.textRenderer, badge, drawX, y, 0xFFFFFFFF);
				drawX += client.textRenderer.getWidth(badge) + 6;
				shown++;
			}
		}

		private void drawChangeButton(DrawContext context, MinecraftClient client, UUID playerId,
				int x, int y, int mouseX, int mouseY) {
			boolean hovered = mouseX >= x && mouseX < x + CHANGE_BUTTON_WIDTH
				&& mouseY >= y && mouseY < y + TAG_BUTTON_HEIGHT;
			boolean expanded = playerId.equals(expandedPlayer);
			context.fill(x, y, x + CHANGE_BUTTON_WIDTH, y + TAG_BUTTON_HEIGHT,
				expanded ? 0xAA222222 : hovered ? 0x77333333 : 0x55222222);
			String label = client.textRenderer.trimToWidth(
				Text.translatable("gui.gexpress.config.players.change_tag").getString(), CHANGE_BUTTON_WIDTH - 8);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label),
				x + CHANGE_BUTTON_WIDTH / 2, y + 4, 0xFFFFFFFF);
			changeButtons.add(new ChangeButton(x, y, CHANGE_BUTTON_WIDTH, TAG_BUTTON_HEIGHT, playerId));
		}

		private void drawLevelEditor(DrawContext context, MinecraftClient client, UUID playerId, String playerName,
				int x, int y, int editorWidth, int mouseX, int mouseY) {
			LevelSnapshot snapshot = levelSnapshot(client, playerId);
			LevelDraft draft = levelDraft(playerId, snapshot);
			context.fill(x, y + 4, x + editorWidth, y + LEVEL_EDITOR_HEIGHT - 8, 0x33212A35);
			context.drawBorder(x, y + 4, editorWidth, LEVEL_EDITOR_HEIGHT - 12, 0x55758AA0);
			context.drawTextWithShadow(client.textRenderer, Text.literal("Level"), x + 8, y + 12, 0xFFB8C3CC);
			context.drawTextWithShadow(client.textRenderer, Text.literal("XP"), x + 8, y + 33, 0xFFB8C3CC);

			int fieldX = x + 56;
			int levelY = y + 8;
			int xpY = y + 29;
			drawLevelField(context, client, playerId, playerName, LevelFieldKind.LEVEL, draft.levelText,
				fieldX, levelY, LEVEL_FIELD_WIDTH, mouseX, mouseY);
			drawLevelApply(context, client, playerId, playerName, LevelFieldKind.LEVEL,
				fieldX + LEVEL_FIELD_WIDTH + 5, levelY, mouseX, mouseY);

			drawLevelField(context, client, playerId, playerName, LevelFieldKind.XP, draft.xpText,
				fieldX, xpY, LEVEL_FIELD_WIDTH, mouseX, mouseY);
			context.drawTextWithShadow(client.textRenderer, Text.literal("/ " + snapshot.neededXp()),
				fieldX + LEVEL_FIELD_WIDTH + 7, xpY + 5, 0xFF8FA1B2);
			drawLevelApply(context, client, playerId, playerName, LevelFieldKind.XP,
				fieldX + LEVEL_FIELD_WIDTH + 52, xpY, mouseX, mouseY);

			int barX = Math.min(x + editorWidth - 114, fieldX + LEVEL_FIELD_WIDTH + 96);
			int barY = y + 16;
			int barW = Math.max(52, x + editorWidth - barX - 10);
			float progress = snapshot.neededXp() <= 0 ? 0.0F
				: Math.min(1.0F, snapshot.levelXp() / (float) snapshot.neededXp());
			context.fill(barX, barY, barX + barW, barY + 5, 0xAA0B1016);
			context.fill(barX, barY, barX + Math.round(barW * progress), barY + 5, 0xFF7CC9A2);
			context.drawTextWithShadow(client.textRenderer,
				Text.literal("Lv " + snapshot.level() + "  " + snapshot.levelXp() + "/" + snapshot.neededXp()),
				barX, barY + 10, 0xFFB8C3CC);
		}

		private void drawLevelField(DrawContext context, MinecraftClient client, UUID playerId, String playerName,
				LevelFieldKind kind, String text, int x, int y, int fieldWidth, int mouseX, int mouseY) {
			boolean hovered = mouseX >= x && mouseX < x + fieldWidth && mouseY >= y && mouseY < y + TAG_BUTTON_HEIGHT;
			boolean focused = focusedLevelField != null && focusedLevelField.matches(playerId, kind);
			context.fill(x, y, x + fieldWidth, y + TAG_BUTTON_HEIGHT,
				focused ? 0xDD182538 : hovered ? 0xBB17202B : 0x99101720);
			context.drawBorder(x, y, fieldWidth, TAG_BUTTON_HEIGHT, focused ? 0xFFE2F0FF : 0x88758AA0);
			String visible = client.textRenderer.trimToWidth(text == null ? "" : text, fieldWidth - 8);
			context.drawTextWithShadow(client.textRenderer, Text.literal(visible), x + 4, y + 5, 0xFFFFFFFF);
			if (focused && (System.currentTimeMillis() / 450L) % 2L == 0L) {
				int cursorX = x + 4 + client.textRenderer.getWidth(visible);
				context.fill(cursorX, y + 4, cursorX + 1, y + TAG_BUTTON_HEIGHT - 4, 0xFFFFFFFF);
			}
			levelFields.add(new LevelFieldBox(x, y, fieldWidth, TAG_BUTTON_HEIGHT, playerId, playerName, kind));
		}

		private void drawLevelApply(DrawContext context, MinecraftClient client, UUID playerId, String playerName,
				LevelFieldKind kind, int x, int y, int mouseX, int mouseY) {
			boolean hovered = mouseX >= x && mouseX < x + LEVEL_APPLY_WIDTH && mouseY >= y && mouseY < y + TAG_BUTTON_HEIGHT;
			context.fill(x, y, x + LEVEL_APPLY_WIDTH, y + TAG_BUTTON_HEIGHT, hovered ? 0xAA314152 : 0x77314152);
			context.drawBorder(x, y, LEVEL_APPLY_WIDTH, TAG_BUTTON_HEIGHT, hovered ? 0xFFDAE8F7 : 0x8890A0AA);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Set"),
				x + LEVEL_APPLY_WIDTH / 2, y + 5, 0xFFFFFFFF);
			levelApplyButtons.add(new LevelApplyButton(x, y, LEVEL_APPLY_WIDTH, TAG_BUTTON_HEIGHT, playerId,
				playerName, kind));
		}

		private void drawTagDropdown(DrawContext context, MinecraftClient client, UUID playerId, String playerName,
				List<GexpressPermissions.TagInfo> current, int x, int y, int dropdownWidth, int mouseX, int mouseY) {
			List<TagOption> options = editableTags(client);
			int columns = dropdownColumns(dropdownWidth);
			int bx = x;
			int by = y + 4;
			for (int i = 0; i < options.size(); i++) {
				TagOption option = options.get(i);
				boolean selected = current.stream().anyMatch(tag -> option.id().equals(tag.id()));
				drawTagButton(context, client, playerId, playerName, option, selected, bx, by, mouseX, mouseY);
				if ((i + 1) % columns == 0) {
					bx = x;
					by += TAG_BUTTON_HEIGHT + TAG_BUTTON_GAP;
				} else {
					bx += TAG_BUTTON_WIDTH + TAG_BUTTON_GAP;
				}
			}
		}

		private void drawTagButton(DrawContext context, MinecraftClient client, UUID playerId, String playerName,
				TagOption tag, boolean selected, int x, int y, int mouseX, int mouseY) {
			boolean hovered = mouseX >= x && mouseX < x + TAG_BUTTON_WIDTH
				&& mouseY >= y && mouseY < y + TAG_BUTTON_HEIGHT;
			int base = selected ? 0xAA222222 : hovered ? 0x77333333 : 0x55222222;
			context.fill(x, y, x + TAG_BUTTON_WIDTH, y + TAG_BUTTON_HEIGHT, base);
			context.fill(x, y + TAG_BUTTON_HEIGHT - 2, x + TAG_BUTTON_WIDTH, y + TAG_BUTTON_HEIGHT,
				0xFF000000 | tag.color());
			String label = client.textRenderer.trimToWidth(tag.displayName(), TAG_BUTTON_WIDTH - 6);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label),
				x + TAG_BUTTON_WIDTH / 2, y + 4, 0xFFFFFFFF);
			tagButtons.add(new TagButton(x, y, TAG_BUTTON_WIDTH, TAG_BUTTON_HEIGHT, playerId, playerName, tag, selected));
		}

		private void drawHead(DrawContext context, PlayerListEntry entry, int x, int y) {
			Identifier texture = entry == null || entry.getProfile() == null
				? DefaultSkinHelper.getSkinTextures(UUID.nameUUIDFromBytes(new byte[0])).texture()
				: DefaultSkinHelper.getSkinTextures(entry.getProfile().getId()).texture();
			if (entry != null) texture = entry.getSkinTextures().texture();
			context.drawTexture(texture, x, y, HEAD_SIZE, HEAD_SIZE, 8.0F, 8.0F, 8, 8, 64, 64);
			context.drawTexture(texture, x, y, HEAD_SIZE, HEAD_SIZE, 40.0F, 8.0F, 8, 8, 64, 64);
		}

		private List<PlayerListEntry> onlinePlayers() {
			MinecraftClient client = MinecraftClient.getInstance();
			ClientPlayNetworkHandler network = client == null ? null : client.getNetworkHandler();
			if (network == null) return List.of();
			return network.getPlayerList().stream()
				.sorted(Comparator.comparing(entry -> entry.getProfile().getName(), String.CASE_INSENSITIVE_ORDER))
				.toList();
		}

		private List<GexpressPermissions.TagInfo> currentTags(PlayerListEntry entry) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (entry == null || entry.getProfile() == null) {
				return List.of(GexpressPermissions.TagInfo.from(PlayerTag.PASSENGER));
			}
			return GexpressPermissions.effectiveTagInfos(client == null ? null : client.world,
				entry.getProfile().getId(), entry.getProfile().getName());
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			for (LevelApplyButton applyButton : levelApplyButtons) {
				if (!applyButton.contains(mouseX, mouseY)) continue;
				submitLevelField(applyButton.playerId(), applyButton.playerName(), applyButton.kind());
				return true;
			}
			for (LevelFieldBox field : levelFields) {
				if (!field.contains(mouseX, mouseY)) continue;
				focusedLevelField = new FocusedLevelField(field.playerId(), field.playerName(), field.kind());
				setFocused(true);
				return true;
			}
			for (TagButton tagButton : tagButtons) {
				if (!tagButton.contains(mouseX, mouseY)) continue;
				sendTagCommand(tagButton.playerName(), tagButton.tag(), tagButton.selected());
				scroll = clampScroll(scroll);
				return true;
			}
			for (ChangeButton changeButton : changeButtons) {
				if (!changeButton.contains(mouseX, mouseY)) continue;
				expandedPlayer = changeButton.playerId().equals(expandedPlayer) ? null : changeButton.playerId();
				scroll = clampScroll(scroll);
				return true;
			}
			focusedLevelField = null;
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (focusedLevelField == null) return super.keyPressed(keyCode, scanCode, modifiers);
			LevelDraft draft = levelDrafts.get(focusedLevelField.playerId());
			if (draft == null) return true;
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				submitLevelField(focusedLevelField.playerId(), focusedLevelField.playerName(), focusedLevelField.kind());
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				focusedLevelField = null;
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
				String value = focusedLevelField.kind() == LevelFieldKind.XP ? draft.xpText : draft.levelText;
				value = value == null || value.isEmpty() ? "" : value.substring(0, value.length() - 1);
				draft.set(focusedLevelField.kind(), value);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_DELETE) {
				draft.set(focusedLevelField.kind(), "");
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_V && Screen.hasControlDown()) {
				MinecraftClient client = MinecraftClient.getInstance();
				appendDigits(draft, focusedLevelField.kind(), client == null ? "" : client.keyboard.getClipboard());
				return true;
			}
			return true;
		}

		@Override
		public boolean charTyped(char chr, int modifiers) {
			if (focusedLevelField == null) return super.charTyped(chr, modifiers);
			LevelDraft draft = levelDrafts.get(focusedLevelField.playerId());
			if (draft == null) return true;
			if (Character.isDigit(chr)) appendDigits(draft, focusedLevelField.kind(), Character.toString(chr));
			return true;
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
			if (mouseX < getX() || mouseX >= getX() + width || mouseY < getY() || mouseY >= getY() + height) {
				return false;
			}
			scroll = clampScroll(scroll - (int) Math.round(verticalAmount * 18.0D));
			return true;
		}

		private int rowHeight(PlayerListEntry entry, int rowWidth) {
			List<GexpressPermissions.TagInfo> tags = currentTags(entry);
			MinecraftClient client = MinecraftClient.getInstance();
			boolean dev = tags.stream().anyMatch(tag -> PlayerTag.DEV.id().equals(tag.id()));
			boolean self = isSelf(client, entry);
			if ((dev && !self) || entry == null || entry.getProfile() == null
					|| !entry.getProfile().getId().equals(expandedPlayer)) {
				return ROW_HEIGHT;
			}
			int optionCount = editableTags(client).size();
			int columns = dropdownColumns(rowWidth - 44);
			int rows = (optionCount + columns - 1) / columns;
			return ROW_HEIGHT + LEVEL_EDITOR_HEIGHT + 8 + rows * TAG_BUTTON_HEIGHT
				+ Math.max(0, rows - 1) * TAG_BUTTON_GAP;
		}

		private int dropdownColumns(int dropdownWidth) {
			int options = editableTags(MinecraftClient.getInstance()).size();
			return Math.max(1, Math.min(Math.max(1, options),
				(dropdownWidth + TAG_BUTTON_GAP) / (TAG_BUTTON_WIDTH + TAG_BUTTON_GAP)));
		}

		private int clampScroll(int requested) {
			List<PlayerListEntry> entries = onlinePlayers();
			int listWidth = width - 24;
			int viewportHeight = Math.max(0, height - 50);
			return clampScroll(requested, entries, listWidth, viewportHeight);
		}

		private int clampScroll(int requested, List<PlayerListEntry> entries, int rowWidth, int viewportHeight) {
			int contentHeight = 0;
			for (PlayerListEntry entry : entries) {
				contentHeight += rowHeight(entry, rowWidth);
			}
			int maxScroll = Math.max(0, contentHeight - Math.max(0, viewportHeight));
			return Math.max(0, Math.min(requested, maxScroll));
		}

		private void sendTagCommand(String playerName, TagOption tag, boolean selected) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.player.networkHandler == null
					|| tag == null || PlayerTag.DEV.id().equals(tag.id())
					|| PlayerTag.PASSENGER.id().equals(tag.id())) return;
			String action = selected ? "remove" : "add";
			client.player.networkHandler.sendChatCommand("g admin tag " + action + " " + tag.id() + " " + playerName);
		}

		private void submitLevelField(UUID playerId, String playerName, LevelFieldKind kind) {
			LevelDraft draft = levelDrafts.get(playerId);
			if (draft == null) return;
			Integer value = parsePositiveInt(kind == LevelFieldKind.XP ? draft.xpText : draft.levelText);
			if (value == null) return;
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.player.networkHandler == null) return;
			String subcommand = kind == LevelFieldKind.XP ? "xp" : "level";
			client.player.networkHandler.sendChatCommand("g admin level " + subcommand + " " + playerName + " " + value);
			focusedLevelField = null;
		}

		private LevelSnapshot levelSnapshot(MinecraftClient client, UUID playerId) {
			LevelComponent levels = client == null || client.world == null ? null : LevelComponent.KEY.getNullable(client.world);
			if (levels == null || playerId == null) return new LevelSnapshot(1, 0, LevelComponent.xpNeededForLevel(1));
			return new LevelSnapshot(levels.level(playerId), levels.xpIntoLevel(playerId),
				levels.xpNeededForNextLevel(playerId));
		}

		private LevelDraft levelDraft(UUID playerId, LevelSnapshot snapshot) {
			LevelDraft draft = levelDrafts.computeIfAbsent(playerId, id -> new LevelDraft());
			if (focusedLevelField == null || !focusedLevelField.matches(playerId, LevelFieldKind.XP)) {
				draft.xpText = Integer.toString(snapshot.levelXp());
			}
			if (focusedLevelField == null || !focusedLevelField.matches(playerId, LevelFieldKind.LEVEL)) {
				draft.levelText = Integer.toString(snapshot.level());
			}
			return draft;
		}

		private void appendDigits(LevelDraft draft, LevelFieldKind kind, String raw) {
			if (raw == null || raw.isEmpty()) return;
			StringBuilder out = new StringBuilder(kind == LevelFieldKind.XP ? draft.xpText : draft.levelText);
			for (int i = 0; i < raw.length() && out.length() < 9; i++) {
				char c = raw.charAt(i);
				if (Character.isDigit(c)) out.append(c);
			}
			draft.set(kind, out.toString());
		}

		private Integer parsePositiveInt(String raw) {
			if (raw == null || raw.isBlank()) return null;
			try {
				return Math.max(0, Integer.parseInt(raw.trim()));
			} catch (NumberFormatException ignored) {
				return null;
			}
		}

		private List<TagOption> editableTags(MinecraftClient client) {
			List<TagOption> options = new ArrayList<>();
			PlayerTagComponent component = null;
			if (client != null && client.world != null) {
				component = PlayerTagComponent.KEY.getNullable(client.world);
			}
			for (PlayerTag tag : BUILTIN_TAGS) options.add(TagOption.from(tag, component));
			if (client != null && client.world != null) {
				if (component != null) {
					component.getCustomTags().values().stream()
						.sorted(Comparator.comparingInt(PlayerTagComponent.CustomTag::priority).reversed())
						.map(TagOption::from)
						.forEach(options::add);
				}
			}
			return options.stream()
				.sorted(Comparator.comparingInt(TagOption::priority).reversed()
					.thenComparing(TagOption::displayName, String.CASE_INSENSITIVE_ORDER))
				.toList();
		}

		private boolean isSelf(MinecraftClient client, PlayerListEntry entry) {
			if (client == null || client.player == null || entry == null || entry.getProfile() == null) return false;
			if (client.player.getUuid().equals(entry.getProfile().getId())) return true;
			return client.player.getGameProfile().getName().equalsIgnoreCase(entry.getProfile().getName());
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	private record TagButton(int x, int y, int width, int height, UUID playerId, String playerName, TagOption tag,
			boolean selected) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record ChangeButton(int x, int y, int width, int height, UUID playerId) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record LevelFieldBox(int x, int y, int width, int height, UUID playerId, String playerName,
			LevelFieldKind kind) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record LevelApplyButton(int x, int y, int width, int height, UUID playerId, String playerName,
			LevelFieldKind kind) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record FocusedLevelField(UUID playerId, String playerName, LevelFieldKind kind) {
		private boolean matches(UUID otherPlayerId, LevelFieldKind otherKind) {
			return playerId != null && playerId.equals(otherPlayerId) && kind == otherKind;
		}
	}

	private record LevelSnapshot(int level, int levelXp, int neededXp) {}

	private enum LevelFieldKind {
		XP,
		LEVEL
	}

	private static final class LevelDraft {
		private String xpText = "";
		private String levelText = "";

		private void set(LevelFieldKind kind, String value) {
			if (kind == LevelFieldKind.XP) {
				xpText = value == null ? "" : value;
			} else {
				levelText = value == null ? "" : value;
			}
		}
	}

	private record TagOption(String id, String displayName, int color, int priority) {
		private static TagOption from(PlayerTag tag) {
			return new TagOption(tag.id(), tag.displayName(), tag.color(), tag.priority());
		}

		private static TagOption from(PlayerTag tag, PlayerTagComponent component) {
			if (component == null) return from(tag);
			return new TagOption(tag.id(), tag.displayName(), component.color(tag), component.priority(tag));
		}

		private static TagOption from(PlayerTagComponent.CustomTag tag) {
			return new TagOption(tag.id(), tag.displayName(), tag.color(), tag.priority());
		}
	}
}
