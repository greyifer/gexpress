package dev.mapselect.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public final class GexpressTutorialScreen extends Screen {
	private static final int PANEL = 0xEE121820;
	private static final int BORDER = 0xAA91A0B3;
	private static final int GOLD = 0xFFE7C66A;
	private static final int TEXT = 0xFFE8EDF2;
	private static final int MUTED = 0xFF9DA9B6;

	private final Screen parent;
	private final boolean markSeenOnClose;
	private final List<GexpressTutorialStore.Page> pages;
	private int page;
	private ButtonWidget previousButton;
	private ButtonWidget nextButton;
	private ButtonWidget doneButton;

	public GexpressTutorialScreen(Screen parent, boolean markSeenOnClose) {
		super(Text.literal("G'Express Tutorial"));
		this.parent = parent;
		this.markSeenOnClose = markSeenOnClose;
		this.pages = GexpressTutorialStore.pages();
	}

	@Override
	protected void init() {
		previousButton = addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
			page = Math.max(0, page - 1);
			updateButtons();
		}).build());
		nextButton = addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> {
			page = Math.min(Math.max(0, pages.size() - 1), page + 1);
			updateButtons();
		}).build());
		doneButton = addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).build());
		layoutButtons();
		updateButtons();
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xDD05070A);
		int panelW = MathHelper.clamp(width - 80, 360, 680);
		int panelH = MathHelper.clamp(height - 80, 250, 430);
		int x = (width - panelW) / 2;
		int y = (height - panelH) / 2;
		renderPanel(context, textRenderer, title, pages, page, x, y, panelW, panelH);
		super.render(context, mouseX, mouseY, delta);
	}

	static void renderPanel(DrawContext context, TextRenderer textRenderer, Text screenTitle,
			List<GexpressTutorialStore.Page> pages, int page, int x, int y, int panelW, int panelH) {
		context.fill(x, y, x + panelW, y + panelH, PANEL);
		context.drawBorder(x, y, panelW, panelH, BORDER);
		context.fill(x, y, x + panelW, y + 3, GOLD);
		context.drawCenteredTextWithShadow(textRenderer, screenTitle, x + panelW / 2, y + 14, TEXT);

		if (pages.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal("No tutorial pages configured."), x + panelW / 2,
				y + panelH / 2, MUTED);
			return;
		}

		GexpressTutorialStore.Page current = pages.get(MathHelper.clamp(page, 0, pages.size() - 1));
		int contentX = x + 28;
		int contentY = y + 48;
		context.drawTextWithShadow(textRenderer, Text.literal(current.title()).formatted(Formatting.BOLD),
			contentX, contentY, TEXT);
		context.drawTextWithShadow(textRenderer, Text.literal((page + 1) + " / " + pages.size()).formatted(Formatting.GRAY),
			x + panelW - 62, contentY, MUTED);
		contentY += 24;
		contentY = drawImagePreview(context, textRenderer, current.image(), contentX, contentY, panelW - 56);
		drawWrappedBody(context, textRenderer, current.body(), contentX, contentY + 14, panelW - 56);
	}

	@Override
	public void resize(MinecraftClient client, int width, int height) {
		super.resize(client, width, height);
		layoutButtons();
	}

	@Override
	public void close() {
		if (markSeenOnClose) GexpressTutorialStore.markSeen();
		MinecraftClient.getInstance().setScreen(parent);
	}

	private static int drawImagePreview(DrawContext context, TextRenderer textRenderer, String image, int x, int y,
			int width) {
		if (image == null || image.isBlank()) return y;
		Identifier texture = parseIdentifier(image);
		int h = 92;
		context.fill(x, y, x + width, y + h, 0xAA090D12);
		context.drawBorder(x, y, width, h, 0x665E6D7E);
		if (texture == null) {
			context.drawTextWithShadow(textRenderer, Text.literal(image).formatted(Formatting.GRAY), x + 12, y + 38, MUTED);
			return y + h;
		}
		int size = 64;
		int imageX = x + (width - size) / 2;
		int imageY = y + 14;
		try {
			context.drawTexture(texture, imageX, imageY, size, size, 0.0F, 0.0F, size, size, size, size);
		} catch (RuntimeException ignored) {
			context.drawTextWithShadow(textRenderer, Text.literal(image).formatted(Formatting.GRAY), x + 12, y + 38, MUTED);
		}
		return y + h;
	}

	private static void drawWrappedBody(DrawContext context, TextRenderer tr, String body, int x, int y, int width) {
		List<OrderedText> lines = tr.wrapLines(Text.literal(body == null ? "" : body), width);
		for (int i = 0; i < lines.size(); i++) {
			context.drawTextWithShadow(tr, lines.get(i), x, y + i * 12, i == 0 ? TEXT : MUTED);
		}
	}

	private void layoutButtons() {
		if (previousButton == null) return;
		int panelW = MathHelper.clamp(width - 80, 360, 680);
		int panelH = MathHelper.clamp(height - 80, 250, 430);
		int x = (width - panelW) / 2;
		int y = (height - panelH) / 2 + panelH - 34;
		previousButton.setDimensionsAndPosition(92, 20, x + 24, y);
		nextButton.setDimensionsAndPosition(92, 20, x + panelW - 116, y);
		doneButton.setDimensionsAndPosition(104, 20, x + (panelW - 104) / 2, y);
	}

	private void updateButtons() {
		if (previousButton == null) return;
		previousButton.active = page > 0;
		nextButton.active = page < pages.size() - 1;
	}

	private static Identifier parseIdentifier(String raw) {
		if (raw == null || raw.isBlank()) return null;
		try {
			return Identifier.of(raw.strip());
		} catch (RuntimeException ignored) {
			return null;
		}
	}
}
