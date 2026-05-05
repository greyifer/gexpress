package dev.mapselect.client.screen;

import dev.mapselect.host.PlayerTag;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class GexpressPlayersScreen extends Screen {
	private static final int ROW_HEIGHT = 36;
	private static final int HEAD_SIZE = 24;
	private static final int TAG_BUTTON_WIDTH = 68;
	private static final int TAG_BUTTON_HEIGHT = 16;
	private static final int TAG_BUTTON_GAP = 4;
	private final Screen parent;
	private final List<TagButton> tagButtons = new ArrayList<>();
	private int scroll;

	public GexpressPlayersScreen(Screen parent) {
		super(Text.translatable("gui.gexpress.config.category.players"));
		this.parent = parent;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.translatable("gui.gexpress.config.players.subtitle").formatted(Formatting.GRAY),
			width / 2, 27, 0xA0A0A0);

		tagButtons.clear();
		List<PlayerListEntry> entries = onlinePlayers();
		if (entries.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("gui.gexpress.config.players.empty").formatted(Formatting.DARK_GRAY),
				width / 2, height / 2, 0x777777);
			return;
		}

		int listX = Math.max(16, width / 2 - 360);
		int listWidth = Math.min(width - listX * 2, 720);
		int y = 52 - scroll;
		for (PlayerListEntry entry : entries) {
			if (y > 42 && y < height - 26) drawPlayerRow(context, entry, listX, y, listWidth, mouseX, mouseY);
			y += ROW_HEIGHT;
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() * ROW_HEIGHT - (height - 82))));
	}

	private void drawPlayerRow(DrawContext context, PlayerListEntry entry, int x, int y, int rowWidth,
			int mouseX, int mouseY) {
		UUID id = entry.getProfile().getId();
		String name = entry.getProfile().getName();
		PlayerTag tag = currentTag(entry);
		boolean dev = tag == PlayerTag.DEV;
		boolean hovered = mouseY >= y && mouseY < y + ROW_HEIGHT && mouseX >= x && mouseX < x + rowWidth;
		context.fill(x, y, x + rowWidth, y + ROW_HEIGHT - 3, hovered ? 0x44222222 : 0x33111111);
		drawHead(context, entry, x + 6, y + 5);
		context.drawTextWithShadow(textRenderer, Text.literal(name), x + 38, y + 6, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, GexpressPermissions.tagBadge(tag), x + 38, y + 20, 0xFFFFFFFF);

		if (dev) {
			context.drawTextWithShadow(textRenderer,
				Text.translatable("gui.gexpress.config.players.locked").formatted(Formatting.DARK_GRAY),
				x + rowWidth - 78, y + 13, 0xFF777777);
			return;
		}

		PlayerTag[] tags = {
			PlayerTag.OWNER,
			PlayerTag.STAFF,
			PlayerTag.HOST,
			PlayerTag.TRUSTED,
			PlayerTag.BUILDER,
			PlayerTag.DESIGNER,
			PlayerTag.PASSENGER
		};
		int totalWidth = tags.length * TAG_BUTTON_WIDTH + (tags.length - 1) * TAG_BUTTON_GAP;
		int bx = x + rowWidth - totalWidth - 6;
		int by = y + 10;
		for (PlayerTag option : tags) {
			drawTagButton(context, id, name, option, tag == option, bx, by, mouseX, mouseY);
			bx += TAG_BUTTON_WIDTH + TAG_BUTTON_GAP;
		}
	}

	private void drawTagButton(DrawContext context, UUID playerId, String playerName, PlayerTag tag,
			boolean selected, int x, int y, int mouseX, int mouseY) {
		boolean hovered = mouseX >= x && mouseX < x + TAG_BUTTON_WIDTH
			&& mouseY >= y && mouseY < y + TAG_BUTTON_HEIGHT;
		int base = selected ? 0xAA222222 : hovered ? 0x77333333 : 0x55222222;
		context.fill(x, y, x + TAG_BUTTON_WIDTH, y + TAG_BUTTON_HEIGHT, base);
		context.fill(x, y + TAG_BUTTON_HEIGHT - 2, x + TAG_BUTTON_WIDTH, y + TAG_BUTTON_HEIGHT,
			0xFF000000 | tag.color());
		String label = textRenderer.trimToWidth(tag.displayName(), TAG_BUTTON_WIDTH - 6);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
			x + TAG_BUTTON_WIDTH / 2, y + 4, 0xFFFFFFFF);
		tagButtons.add(new TagButton(x, y, TAG_BUTTON_WIDTH, TAG_BUTTON_HEIGHT, playerId, playerName, tag));
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

	private PlayerTag currentTag(PlayerListEntry entry) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (entry == null || entry.getProfile() == null) return PlayerTag.PASSENGER;
		return GexpressPermissions.effectiveTag(client == null ? null : client.world,
			entry.getProfile().getId(), entry.getProfile().getName());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (TagButton tagButton : tagButtons) {
			if (!tagButton.contains(mouseX, mouseY)) continue;
			sendTagCommand(tagButton.playerName(), tagButton.tag());
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, scroll - (int) Math.round(verticalAmount * 18.0D));
		return true;
	}

	private void sendTagCommand(String playerName, PlayerTag tag) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.player.networkHandler == null
				|| tag == null || tag == PlayerTag.DEV) return;
		client.player.networkHandler.sendChatCommand("g group tag set " + tag.id() + " " + playerName);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	private record TagButton(int x, int y, int width, int height, UUID playerId, String playerName, PlayerTag tag) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}
}
