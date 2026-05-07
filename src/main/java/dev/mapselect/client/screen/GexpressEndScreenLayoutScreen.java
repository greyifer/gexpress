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
	private EndScreenLayoutConfig.Layout draft = EndScreenLayoutConfig.snapshot();
	private EndScreenLayoutConfig.Kind dragging;
	private ButtonWidget saveButton;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean dirty;

	public GexpressEndScreenLayoutScreen(Screen parent) {
		super(Text.translatable("gui.gexpress.end_layout.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int buttonY = height - 28;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.end_layout.reset"), button -> {
				draft = EndScreenLayoutConfig.defaultsSnapshot();
				dirty = true;
				updateSaveButton();
			})
			.dimensions(width / 2 - 150, buttonY, 88, 20)
			.build());
		saveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.end_layout.save"), button ->
				saveDraft())
			.dimensions(width / 2 - 44, buttonY, 88, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
			.dimensions(width / 2 + 62, buttonY, 88, 20)
			.build());
		updateSaveButton();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0x66000000);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.translatable("gui.gexpress.end_layout.subtitle").formatted(Formatting.GRAY),
			width / 2, 29, 0xA0A0A0);

		int originX = width / 2;
		int originY = height / 2 - 40;
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("The Mafia Wins!").formatted(Formatting.GRAY),
			originX, originY - 31, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("They tied all of their loose ends!"),
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

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	private void drawSection(DrawContext context, EndScreenLayoutConfig.Kind kind, Text title,
			int originX, int originY, int mouseX, int mouseY) {
		EndScreenLayoutConfig.Section section = EndScreenLayoutConfig.section(draft, kind);
		int x = originX + section.x;
		int y = originY + section.y;
		boolean hovered = hit(kind, mouseX, mouseY, originX, originY);
		int width = Math.max(44, textRenderer.getWidth(title) + 8);
		if (dragging == kind || hovered) {
			int color = dragging == kind ? 0xCC66CCFF : 0x99FFFFFF;
			context.fill(x - width / 2, y + 10, x + width / 2, y + 11, color);
		}
		context.drawCenteredTextWithShadow(textRenderer, title, x, y, 0xFFFFFF);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int originX = width / 2;
			int originY = height / 2 - 40;
			for (EndScreenLayoutConfig.Kind kind : EndScreenLayoutConfig.Kind.values()) {
				if (hit(kind, mouseX, mouseY, originX, originY)) {
					EndScreenLayoutConfig.Section section = EndScreenLayoutConfig.section(draft, kind);
					dragging = kind;
					dragOffsetX = (int) Math.round(mouseX) - (originX + section.x);
					dragOffsetY = (int) Math.round(mouseY) - (originY + section.y);
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
		int x = snap(mouseX - dragOffsetX - originX);
		int y = snap(mouseY - dragOffsetY - originY);
		EndScreenLayoutConfig.Section section = EndScreenLayoutConfig.section(draft, dragging);
		section.x = clamp(x, -180, 180);
		section.y = clamp(y, -20, 150);
		dirty = true;
		updateSaveButton();
	}

	private boolean hit(EndScreenLayoutConfig.Kind kind, double mouseX, double mouseY, int originX, int originY) {
		EndScreenLayoutConfig.Section section = EndScreenLayoutConfig.section(draft, kind);
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
			&& mouseY >= y - 5 && mouseY <= y + 13;
	}

	private void saveDraft() {
		EndScreenLayoutConfig.apply(draft);
		draft = EndScreenLayoutConfig.snapshot();
		dirty = false;
		updateSaveButton();
	}

	private void updateSaveButton() {
		if (saveButton != null) {
			saveButton.active = dirty;
		}
	}

	private static int snap(double value) {
		return (int) Math.round(value / GRID) * GRID;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	@Override
	public void close() {
		if (dirty) {
			saveDraft();
		}
		MinecraftClient.getInstance().setScreen(parent);
	}
}
