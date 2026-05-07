package dev.mapselect.client.screen;

import dev.mapselect.client.EndScreenLayoutConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GexpressEndScreenLayoutScreen extends Screen {
	private static final int GRID = 4;
	private final Screen parent;
	private EndScreenLayoutConfig.Kind dragging;

	public GexpressEndScreenLayoutScreen(Screen parent) {
		super(Text.translatable("gui.gexpress.end_layout.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int buttonY = height - 28;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.end_layout.reset"), button -> {
				EndScreenLayoutConfig.reset();
			})
			.dimensions(width / 2 - 104, buttonY, 96, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
			.dimensions(width / 2 + 8, buttonY, 96, 20)
			.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.translatable("gui.gexpress.end_layout.subtitle").formatted(Formatting.GRAY),
			width / 2, 29, 0xA0A0A0);

		int originX = width / 2;
		int originY = height / 2 - 40;
		drawGrid(context, originX, originY);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Passengers Win!").formatted(Formatting.GREEN),
			originX, originY - 31, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("All killers were eliminated; the passengers win!"),
			originX, originY - 8, 0xFFFFFF);

		drawSection(context, EndScreenLayoutConfig.Kind.CIVILIANS,
			Text.literal("Civilians").formatted(Formatting.GREEN), originX, originY, mouseX, mouseY);
		drawSection(context, EndScreenLayoutConfig.Kind.VIGILANTES,
			Text.literal("Vigilantes").formatted(Formatting.AQUA), originX, originY, mouseX, mouseY);
		drawSection(context, EndScreenLayoutConfig.Kind.NEUTRALS,
			Text.literal("Neutrals").formatted(Formatting.GOLD), originX, originY, mouseX, mouseY);
		drawSection(context, EndScreenLayoutConfig.Kind.KILLERS,
			Text.literal("Killers").formatted(Formatting.RED), originX, originY, mouseX, mouseY);
		drawSection(context, EndScreenLayoutConfig.Kind.MAFIA,
			Text.literal("Mafia").formatted(Formatting.DARK_GRAY), originX, originY, mouseX, mouseY);

		super.render(context, mouseX, mouseY, delta);
	}

	private void drawGrid(DrawContext context, int originX, int originY) {
		int minX = originX - 190;
		int maxX = originX + 190;
		int minY = originY - 24;
		int maxY = originY + 154;
		context.fill(minX, minY, maxX, maxY, 0x66000000);
		for (int x = minX; x <= maxX; x += GRID) {
			int color = x == originX ? 0x55FFFFFF : 0x18222222;
			context.fill(x, minY, x + 1, maxY, color);
		}
		for (int y = minY; y <= maxY; y += GRID) {
			int color = y == originY ? 0x55FFFFFF : 0x18222222;
			context.fill(minX, y, maxX, y + 1, color);
		}
	}

	private void drawSection(DrawContext context, EndScreenLayoutConfig.Kind kind, Text title,
			int originX, int originY, int mouseX, int mouseY) {
		EndScreenLayoutConfig.Section section = EndScreenLayoutConfig.section(kind);
		int x = originX + section.x;
		int y = originY + section.y;
		boolean hovered = hit(kind, mouseX, mouseY, originX, originY);
		int width = Math.max(44, textRenderer.getWidth(title) + 8);
		int color = dragging == kind ? 0xAA66CCFF : hovered ? 0x88FFFFFF : 0x66000000;
		context.fill(x - width / 2, y - 3, x + width / 2, y + 20, color);
		context.drawCenteredTextWithShadow(textRenderer, title, x, y, 0xFFFFFF);
		for (int i = 0; i < Math.max(1, section.columns); i++) {
			int px = x - section.columns * 6 + i * 12;
			context.fill(px, y + 11, px + 8, y + 19, 0xFFBDBDBD);
			context.fill(px + 1, y + 12, px + 7, y + 18, 0xFF5B6F8C);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int originX = width / 2;
			int originY = height / 2 - 40;
			for (EndScreenLayoutConfig.Kind kind : EndScreenLayoutConfig.Kind.values()) {
				if (hit(kind, mouseX, mouseY, originX, originY)) {
					dragging = kind;
					moveDragging(mouseX, mouseY);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && dragging != null) {
			moveDragging(mouseX, mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && dragging != null) {
			moveDragging(mouseX, mouseY);
			dragging = null;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void moveDragging(double mouseX, double mouseY) {
		if (dragging == null) return;
		int originX = width / 2;
		int originY = height / 2 - 40;
		int x = (int) Math.round((mouseX - originX) / GRID) * GRID;
		int y = (int) Math.round((mouseY - originY) / GRID) * GRID;
		EndScreenLayoutConfig.set(dragging, x, y);
	}

	private boolean hit(EndScreenLayoutConfig.Kind kind, double mouseX, double mouseY, int originX, int originY) {
		EndScreenLayoutConfig.Section section = EndScreenLayoutConfig.section(kind);
		Text title = switch (kind) {
			case CIVILIANS -> Text.literal("Civilians");
			case VIGILANTES -> Text.literal("Vigilantes");
			case NEUTRALS -> Text.literal("Neutrals");
			case KILLERS -> Text.literal("Killers");
			case MAFIA -> Text.literal("Mafia");
		};
		int x = originX + section.x;
		int y = originY + section.y;
		int w = Math.max(44, textRenderer.getWidth(title) + 8);
		return mouseX >= x - w / 2.0 && mouseX <= x + w / 2.0
			&& mouseY >= y - 4 && mouseY <= y + 22;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
