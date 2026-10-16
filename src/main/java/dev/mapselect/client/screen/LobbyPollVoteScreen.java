package dev.mapselect.client.screen;

import dev.mapselect.client.poll.ClientLobbyPollState;
import dev.mapselect.network.poll.LobbyPollStatusPayload;
import dev.mapselect.network.poll.LobbyPollVotePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.util.List;

public final class LobbyPollVoteScreen extends Screen {
	private static final int PANEL_W = 226;
	private static final int PANEL_H = 124;
	private static final int TAB_W = 18;
	private static final int TAB_H = 42;
	private LobbyPollStatusPayload payload;
	private final long openedAtMs = Util.getMeasuringTimeMs();
	private int slideFromX = Integer.MIN_VALUE;
	private int slideToX = Integer.MIN_VALUE;
	private long slideStartedAtMs = openedAtMs;
	private boolean minimizing;
	private boolean minimizeFinished;

	public LobbyPollVoteScreen(LobbyPollStatusPayload payload) {
		super(Text.literal("Lobby Poll"));
		this.payload = payload;
	}

	public void refresh(LobbyPollStatusPayload payload) {
		this.payload = payload;
	}

	public void beginMinimize() {
		if (minimizeFinished || minimizing) return;
		minimizing = true;
		slideFromX = currentSlideX();
		slideToX = width + TAB_W + 18;
		slideStartedAtMs = Util.getMeasuringTimeMs();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		int x = panelX();
		int y = panelY();
		if (x < width) {
			context.fill(x, y, x + PANEL_W, y + PANEL_H, 0xEE151B24);
			context.drawBorder(x, y, PANEL_W, PANEL_H, 0xAAE7EEF8);
			context.drawTextWithShadow(textRenderer, Text.literal("Lobby Poll").formatted(Formatting.GOLD, Formatting.BOLD),
				x + 12, y + 10, 0xFFFFD57A);
			context.drawTextWithShadow(textRenderer, Text.literal(timeLabel()).formatted(Formatting.GRAY),
				x + PANEL_W - 12 - textRenderer.getWidth(timeLabel()), y + 10, 0xFF9EACB9);
			List<OrderedText> titleLines = textRenderer.wrapLines(Text.literal(payload.title()), PANEL_W - 24);
			for (int i = 0; i < Math.min(3, titleLines.size()); i++) {
				context.drawTextWithShadow(textRenderer, titleLines.get(i), x + 12, y + 28 + i * 11,
					0xFFFFFFFF);
			}
			context.drawTextWithShadow(textRenderer, Text.literal(payload.yesVotes() + " yes").formatted(Formatting.GREEN),
				x + 12, y + 68, 0xFF62D66E);
			context.drawTextWithShadow(textRenderer, Text.literal(payload.noVotes() + " no").formatted(Formatting.RED),
				x + 88, y + 68, 0xFFFF6B6B);
			context.drawTextWithShadow(textRenderer, Text.literal("Needs " + (payload.playerCount() / 2 + 1))
				.formatted(Formatting.GRAY), x + 142, y + 68, 0xFF9EACB9);
			drawButton(context, "Yes", yesX(), buttonY(), 78, mouseX, mouseY, 0xFF2F7D42);
			drawButton(context, "No", noX(), buttonY(), 78, mouseX, mouseY, 0xFF81343C);
			drawButton(context, "X", x + PANEL_W - 28, y + PANEL_H - 28, 18, mouseX, mouseY, 0xFF3C4652);
		}
		drawSlideTab(context, mouseX, mouseY);
		if (minimizing && slideDone()) {
			minimizeFinished = true;
			ClientLobbyPollState.finishMinimize(MinecraftClient.getInstance(), this);
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
		int x = panelX();
		int y = panelY();
		if (inside(mouseX, mouseY, tabX(), tabY(), TAB_W, TAB_H)) {
			ClientLobbyPollState.minimizeVoteScreen(MinecraftClient.getInstance());
			return true;
		}
		if (inside(mouseX, mouseY, yesX(), buttonY(), 78, 20)) {
			sendVote(true);
			return true;
		}
		if (inside(mouseX, mouseY, noX(), buttonY(), 78, 20)) {
			sendVote(false);
			return true;
		}
		if (inside(mouseX, mouseY, x + PANEL_W - 28, y + PANEL_H - 28, 18, 20)) {
			ClientLobbyPollState.minimizeVoteScreen(MinecraftClient.getInstance());
			return true;
		}
		if (!inside(mouseX, mouseY, x, y, PANEL_W, PANEL_H)) {
			ClientLobbyPollState.minimizeVoteScreen(MinecraftClient.getInstance());
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void close() {
		if (minimizeFinished) {
			super.close();
			return;
		}
		ClientLobbyPollState.minimizeVoteScreen(MinecraftClient.getInstance());
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private int panelX() {
		if (minimizing) return currentSlideX();
		int target = width - PANEL_W - 14;
		if (slideToX != target) {
			slideFromX = slideToX == Integer.MIN_VALUE ? width + 18 : currentSlideX();
			slideToX = target;
			slideStartedAtMs = Util.getMeasuringTimeMs();
		}
		return currentSlideX();
	}

	private int currentSlideX() {
		if (slideToX == Integer.MIN_VALUE) return width + 18;
		float progress = Math.min(1.0F, Math.max(0.0F, (Util.getMeasuringTimeMs() - slideStartedAtMs) / 220.0F));
		float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
		return Math.round(slideFromX + (slideToX - slideFromX) * eased);
	}

	private boolean slideDone() {
		return Util.getMeasuringTimeMs() - slideStartedAtMs >= 220L;
	}

	private int panelY() {
		return Math.max(18, height / 2 - PANEL_H / 2);
	}

	private int buttonY() {
		return panelY() + PANEL_H - 30;
	}

	private int yesX() {
		return panelX() + 12;
	}

	private int noX() {
		return panelX() + 98;
	}

	private String timeLabel() {
		return Math.max(0, (payload.remainingTicks() + 19) / 20) + "s";
	}

	private void drawSlideTab(DrawContext context, int mouseX, int mouseY) {
		int x = tabX();
		int y = tabY();
		boolean hovered = inside(mouseX, mouseY, x, y, TAB_W, TAB_H);
		context.fill(x, y, x + TAB_W, y + TAB_H, hovered ? 0xEE2D3846 : 0xDD151B24);
		context.drawBorder(x, y, TAB_W, TAB_H, hovered ? 0xFFFFFFFF : 0xAAE7EEF8);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.literal(">").formatted(Formatting.BOLD),
			x + TAB_W / 2, y + TAB_H / 2 - 4, 0xFFFFFFFF);
	}

	private int tabX() {
		return Math.max(0, panelX() - TAB_W);
	}

	private int tabY() {
		return panelY() + PANEL_H / 2 - TAB_H / 2;
	}

	private void drawButton(DrawContext context, String label, int x, int y, int w, int mouseX, int mouseY, int color) {
		boolean hovered = inside(mouseX, mouseY, x, y, w, 20);
		context.fill(x, y, x + w, y + 20, hovered ? color : (0xCC000000 | (color & 0xFFFFFF)));
		context.drawBorder(x, y, w, 20, hovered ? 0xFFFFFFFF : 0x88FFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(label).formatted(Formatting.BOLD),
			x + w / 2, y + 6, 0xFFFFFFFF);
	}

	private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private void sendVote(boolean approve) {
		if (ClientPlayNetworking.canSend(LobbyPollVotePayload.ID)) {
			ClientPlayNetworking.send(new LobbyPollVotePayload(approve));
		}
		ClientLobbyPollState.minimizeVoteScreen(MinecraftClient.getInstance());
	}
}
