package dev.mapselect.client.screen;

import dev.mapselect.client.tutorial.ClientTutorialExperience;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GexpressTutorialEditorScreen extends Screen {
	private static final int PANEL = 0xEE121820;
	private static final int BORDER = 0x885E6D7E;
	private static final int GOLD = 0xFFE7C66A;
	private static final int TEXT = 0xFFE8EDF2;
	private static final int MUTED = 0xFF9DA9B6;

	private final Screen parent;
	private ButtonWidget enabledButton;

	public GexpressTutorialEditorScreen(Screen parent) {
		super(Text.literal("Tutorial Room Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int panelX = Math.max(24, width / 2 - 270);
		int panelW = Math.min(540, width - 48);
		int buttonX = panelX + 18;
		int buttonW = panelW - 36;
		int y = 112;

		addDrawableChild(ButtonWidget.builder(Text.literal("Edit Tutorial Room with Axiom"), button ->
			ClientTutorialExperience.editRoomWithAxiom()).dimensions(buttonX, y, buttonW, 22).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Open Axiom Editor"), button -> {
			MinecraftClient.getInstance().setScreen(null);
			ClientTutorialExperience.openAxiom();
		}).dimensions(buttonX, y + 30, buttonW, 20).build()).active = ClientTutorialExperience.axiomInstalled();
		addDrawableChild(ButtonWidget.builder(Text.literal("Save Current Room"), button ->
			ClientTutorialExperience.saveRoom()).dimensions(buttonX, y + 58, (buttonW - 8) / 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Exit Room"), button -> {
			ClientTutorialExperience.requestExit();
			MinecraftClient.getInstance().setScreen(parent);
		}).dimensions(buttonX + (buttonW + 8) / 2, y + 58, (buttonW - 8) / 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Test Tutorial"), button ->
			ClientTutorialExperience.start()).dimensions(buttonX, y + 96, buttonW, 20).build());
		enabledButton = addDrawableChild(ButtonWidget.builder(enabledText(), button -> {
			GexpressTutorialStore.setEnabled(!GexpressTutorialStore.enabled());
			enabledButton.setMessage(enabledText());
		}).dimensions(buttonX, y + 124, (buttonW - 8) / 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Reset First-Time State"), button ->
			GexpressTutorialStore.resetSeen()).dimensions(buttonX + (buttonW + 8) / 2, y + 124,
			(buttonW - 8) / 2, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
			.dimensions(width / 2 - 55, height - 30, 110, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xF00E1116);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, TEXT);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.literal("Build the real tutorial area and place its gameplay markers."), width / 2, 32, MUTED);

		int x = Math.max(24, width / 2 - 270);
		int w = Math.min(540, width - 48);
		int y = 54;
		int h = Math.min(height - 96, 350);
		context.fill(x, y, x + w, y + h, PANEL);
		context.drawBorder(x, y, w, h, BORDER);
		context.fill(x, y, x + w, y + 3, GOLD);
		context.drawTextWithShadow(textRenderer, Text.literal("Axiom Workflow").formatted(Formatting.BOLD),
			x + 18, y + 16, TEXT);
		String axiom = ClientTutorialExperience.axiomInstalled() ? "Axiom 5.4.2+ detected" : "Axiom is not installed";
		context.drawTextWithShadow(textRenderer, Text.literal(axiom), x + 18, y + 31,
			ClientTutorialExperience.axiomInstalled() ? 0xFF74D990 : 0xFFFF7A7A);

		int legendY = 274;
		context.drawTextWithShadow(textRenderer, Text.literal("Placement markers").formatted(Formatting.BOLD),
			x + 18, legendY, TEXT);
		drawLegend(context, x + 18, legendY + 16, 0xFF00A000, "Emerald: tutorial player start");
		drawLegend(context, x + 18, legendY + 30, 0xFFFFD700, "Gold: revolver pickup");
		drawLegend(context, x + 18, legendY + 44, 0xFF55FFFF, "Light blue / red: gun targets");
		drawLegend(context, x + w / 2, legendY + 16, 0xFFFF8000, "Orange: knife-room start");
		drawLegend(context, x + w / 2, legendY + 30, 0xFF202020, "Black: knife target");
		context.drawTextWithShadow(textRenderer,
			Text.literal("Save while you are still inside the edit room. Escape always offers a Leave Tutorial button."),
			x + 18, legendY + 64, MUTED);
		super.render(context, mouseX, mouseY, delta);
	}

	private void drawLegend(DrawContext context, int x, int y, int color, String label) {
		context.fill(x, y, x + 9, y + 9, color);
		context.drawBorder(x, y, 9, 9, 0xFF8995A3);
		context.drawTextWithShadow(textRenderer, Text.literal(label), x + 14, y + 1, MUTED);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	private static Text enabledText() {
		return Text.literal(GexpressTutorialStore.enabled() ? "First-Time Tutorial: Enabled" : "First-Time Tutorial: Disabled");
	}
}
