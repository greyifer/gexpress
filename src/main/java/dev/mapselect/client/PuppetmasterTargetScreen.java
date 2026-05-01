package dev.mapselect.client;

import dev.mapselect.network.PuppetmasterSelectPayload;
import dev.mapselect.network.PuppetmasterTargetsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import java.util.List;

public final class PuppetmasterTargetScreen extends Screen {
	private static final int TILE_WIDTH = 58;
	private static final int TILE_HEIGHT = 50;
	private static final int HEAD_SIZE = 32;
	private static final int GAP = 12;
	private List<PuppetmasterTargetsPayload.Entry> targets = List.of();
	private final List<CardRect> cards = new ArrayList<>();

	public PuppetmasterTargetScreen(List<PuppetmasterTargetsPayload.Entry> targets) {
		super(Text.translatable("gui.gexpress.puppetmaster.title"));
		updateTargets(targets);
	}

	public void updateTargets(List<PuppetmasterTargetsPayload.Entry> next) {
		this.targets = next == null ? List.of() : List.copyOf(next);
		this.cards.clear();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.translatable("gui.gexpress.puppetmaster.subtitle").formatted(Formatting.GRAY),
			width / 2, 31, 0xA0A0A0);

		cards.clear();
		if (targets.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("gui.gexpress.puppetmaster.none").formatted(Formatting.DARK_GRAY),
				width / 2, height / 2, 0x777777);
			return;
		}

		int columns = Math.max(1, Math.min(7, (width - 48) / (TILE_WIDTH + GAP)));
		int rows = (int) Math.ceil(targets.size() / (double) columns);
		int totalWidth = columns * TILE_WIDTH + (columns - 1) * GAP;
		int startX = width / 2 - totalWidth / 2;
		int startY = Math.max(48, height / 2 - rows * (TILE_HEIGHT + GAP) / 2);

		for (int i = 0; i < targets.size(); i++) {
			int col = i % columns;
			int row = i / columns;
			int x = startX + col * (TILE_WIDTH + GAP);
			int y = startY + row * (TILE_HEIGHT + GAP);
			PuppetmasterTargetsPayload.Entry target = targets.get(i);
			cards.add(new CardRect(x, y, TILE_WIDTH, TILE_HEIGHT, target));
			drawTarget(context, target, x, y, mouseX >= x && mouseX < x + TILE_WIDTH && mouseY >= y && mouseY < y + TILE_HEIGHT);
		}
	}

	private void drawTarget(DrawContext context, PuppetmasterTargetsPayload.Entry target, int x, int y, boolean hovered) {
		int headX = x + (TILE_WIDTH - HEAD_SIZE) / 2;
		int headY = y + 2;
		if (hovered) {
			context.fill(headX - 2, headY - 2, headX + HEAD_SIZE + 2, headY + HEAD_SIZE + 2, 0x66FFFFFF);
		}
		drawHead(context, target, headX, headY);
		String name = textRenderer.trimToWidth(target.name(), TILE_WIDTH);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(name), x + TILE_WIDTH / 2, y + 38,
			hovered ? 0xFFFFE0E7 : 0xFFFFFFFF);
	}

	private void drawHead(DrawContext context, PuppetmasterTargetsPayload.Entry target, int x, int y) {
		MinecraftClient client = MinecraftClient.getInstance();
		Identifier texture = DefaultSkinHelper.getSkinTextures(target.id()).texture();
		ClientPlayNetworkHandler network = client == null ? null : client.getNetworkHandler();
		if (network != null) {
			PlayerListEntry entry = network.getPlayerListEntry(target.id());
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		context.drawTexture(texture, x, y, HEAD_SIZE, HEAD_SIZE, 8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, HEAD_SIZE, HEAD_SIZE, 40.0F, 8.0F, 8, 8, 64, 64);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (CardRect card : cards) {
			if (!card.contains(mouseX, mouseY)) continue;
			if (ClientPlayNetworking.canSend(PuppetmasterSelectPayload.ID)) {
				ClientPlayNetworking.send(new PuppetmasterSelectPayload(card.target().id()));
			}
			close();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(null);
	}

	private record CardRect(int x, int y, int width, int height, PuppetmasterTargetsPayload.Entry target) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}
}
