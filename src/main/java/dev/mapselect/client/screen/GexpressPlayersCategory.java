package dev.mapselect.client.screen;

import com.google.common.collect.ImmutableList;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.CustomTabProvider;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.tab.TabExt;
import dev.mapselect.host.PlayerTag;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
		private static final PlayerTag[] EDITABLE_TAGS = {
			PlayerTag.OWNER,
			PlayerTag.STAFF,
			PlayerTag.HOST,
			PlayerTag.TRUSTED,
			PlayerTag.PASSENGER
		};

		private final List<TagButton> tagButtons = new ArrayList<>();
		private final List<ChangeButton> changeButtons = new ArrayList<>();
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
			List<PlayerTag> tags = currentTags(entry);
			PlayerTag tag = tags.isEmpty() ? PlayerTag.PASSENGER : tags.getFirst();
			boolean dev = tags.contains(PlayerTag.DEV);
			boolean expanded = id.equals(expandedPlayer) && !dev;
			boolean hovered = mouseY >= y && mouseY < y + Math.min(rowHeight, ROW_HEIGHT)
				&& mouseX >= x && mouseX < x + rowWidth;
			context.fill(x, y, x + rowWidth, y + rowHeight - 4, hovered || expanded ? 0x44222222 : 0x33111111);
			drawHead(context, entry, x + 6, y + 6);
			context.drawTextWithShadow(client.textRenderer, Text.literal(name), x + 38, y + 7, 0xFFFFFFFF);
			drawTagBadges(context, client, tags, x + 38, y + 21);

			if (dev) {
				context.drawTextWithShadow(client.textRenderer,
					Text.translatable("gui.gexpress.config.players.locked").formatted(Formatting.DARK_GRAY),
					x + rowWidth - 78, y + 14, 0xFF777777);
				return;
			}

			drawChangeButton(context, client, id, x + rowWidth - CHANGE_BUTTON_WIDTH - 6, y + 10, mouseX, mouseY);
			if (expanded) {
				drawTagDropdown(context, client, id, name, tags, x + 38, y + ROW_HEIGHT - 2,
					rowWidth - 44, mouseX, mouseY);
			}
		}

		private void drawTagBadges(DrawContext context, MinecraftClient client, List<PlayerTag> tags, int x, int y) {
			int drawX = x;
			int shown = 0;
			for (PlayerTag tag : tags) {
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

		private void drawTagDropdown(DrawContext context, MinecraftClient client, UUID playerId, String playerName,
				List<PlayerTag> current, int x, int y, int dropdownWidth, int mouseX, int mouseY) {
			int columns = dropdownColumns(dropdownWidth);
			int bx = x;
			int by = y + 4;
			for (int i = 0; i < EDITABLE_TAGS.length; i++) {
				PlayerTag option = EDITABLE_TAGS[i];
				boolean selected = option == PlayerTag.PASSENGER
					? current.size() == 1 && current.contains(PlayerTag.PASSENGER)
					: current.contains(option);
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
				PlayerTag tag, boolean selected, int x, int y, int mouseX, int mouseY) {
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

		private List<PlayerTag> currentTags(PlayerListEntry entry) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (entry == null || entry.getProfile() == null) return List.of(PlayerTag.PASSENGER);
			return GexpressPermissions.effectiveTags(client == null ? null : client.world,
				entry.getProfile().getId(), entry.getProfile().getName());
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			for (TagButton tagButton : tagButtons) {
				if (!tagButton.contains(mouseX, mouseY)) continue;
				sendTagCommand(tagButton.playerName(), tagButton.tag(), tagButton.selected());
				expandedPlayer = null;
				scroll = clampScroll(scroll);
				return true;
			}
			for (ChangeButton changeButton : changeButtons) {
				if (!changeButton.contains(mouseX, mouseY)) continue;
				expandedPlayer = changeButton.playerId().equals(expandedPlayer) ? null : changeButton.playerId();
				scroll = clampScroll(scroll);
				return true;
			}
			return super.mouseClicked(mouseX, mouseY, button);
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
			List<PlayerTag> tags = currentTags(entry);
			if (tags.contains(PlayerTag.DEV) || entry == null || entry.getProfile() == null
					|| !entry.getProfile().getId().equals(expandedPlayer)) {
				return ROW_HEIGHT;
			}
			int rows = (EDITABLE_TAGS.length + dropdownColumns(rowWidth - 44) - 1) / dropdownColumns(rowWidth - 44);
			return ROW_HEIGHT + 8 + rows * TAG_BUTTON_HEIGHT + Math.max(0, rows - 1) * TAG_BUTTON_GAP;
		}

		private int dropdownColumns(int dropdownWidth) {
			return Math.max(1, Math.min(EDITABLE_TAGS.length,
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

		private void sendTagCommand(String playerName, PlayerTag tag, boolean selected) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.player.networkHandler == null
					|| tag == null || tag == PlayerTag.DEV) return;
			String action = tag == PlayerTag.PASSENGER ? "set" : selected ? "remove" : "add";
			client.player.networkHandler.sendChatCommand("g group tag " + action + " " + tag.id() + " " + playerName);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	private record TagButton(int x, int y, int width, int height, UUID playerId, String playerName, PlayerTag tag,
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
}
