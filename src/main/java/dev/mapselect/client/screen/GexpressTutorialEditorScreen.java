package dev.mapselect.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public final class GexpressTutorialEditorScreen extends Screen {
	private static final int PANEL = 0xEE121820;
	private static final int BORDER = 0x885E6D7E;
	private static final int GOLD = 0xFFE7C66A;
	private static final int TEXT = 0xFFE8EDF2;
	private static final int MUTED = 0xFF9DA9B6;

	private final Screen parent;
	private final List<GexpressTutorialStore.Page> pages = new ArrayList<>();
	private int page;
	private TextFieldWidget titleField;
	private TextFieldWidget bodyField;
	private TextFieldWidget imageField;
	private ButtonWidget previousButton;
	private ButtonWidget nextButton;
	private ButtonWidget addButton;
	private ButtonWidget deleteButton;
	private ButtonWidget saveButton;
	private ButtonWidget previewButton;
	private ButtonWidget resetSeenButton;
	private ButtonWidget enabledButton;
	private ButtonWidget doneButton;

	public GexpressTutorialEditorScreen(Screen parent) {
		super(Text.literal("Tutorial Editor"));
		this.parent = parent;
		this.pages.addAll(GexpressTutorialStore.pages());
		if (pages.isEmpty()) pages.add(new GexpressTutorialStore.Page("Tutorial", "", ""));
	}

	@Override
	protected void init() {
		titleField = field("Title", 96);
		bodyField = field("Body", 800);
		imageField = field("Image Texture Id", 180);
		previousButton = addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> switchPage(page - 1)).build());
		nextButton = addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> switchPage(page + 1)).build());
		addButton = addDrawableChild(ButtonWidget.builder(Text.literal("Add Page"), button -> addPage()).build());
		deleteButton = addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), button -> deletePage()).build());
		saveButton = addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> save()).build());
		previewButton = addDrawableChild(ButtonWidget.builder(Text.literal("Preview"), button -> {
			saveCurrentPage();
			GexpressTutorialStore.setPages(pages);
			MinecraftClient.getInstance().setScreen(new GexpressTutorialScreen(this, false));
		}).build());
		resetSeenButton = addDrawableChild(ButtonWidget.builder(Text.literal("Reset Seen"), button -> GexpressTutorialStore.resetSeen()).build());
		enabledButton = addDrawableChild(ButtonWidget.builder(enabledText(), button -> {
			GexpressTutorialStore.setEnabled(!GexpressTutorialStore.enabled());
			enabledButton.setMessage(enabledText());
		}).build());
		doneButton = addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).build());
		layout();
		loadPage();
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xF00E1116);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, TEXT);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.literal("Edit pages on the left and see the tutorial preview update live."),
			width / 2, 31, MUTED);

		int formX = outerX();
		int formY = outerY();
		int formW = formW();
		int formH = outerH();
		context.fill(formX, formY, formX + formW, formY + formH, PANEL);
		context.drawBorder(formX, formY, formW, formH, BORDER);
		context.fill(formX, formY, formX + formW, formY + 3, GOLD);
		context.drawTextWithShadow(textRenderer,
			Text.literal("Page " + (page + 1) + " / " + pages.size()).formatted(Formatting.BOLD),
			formX + 18, formY + 16, TEXT);
		label(context, "Title", titleField);
		label(context, "Body Text", bodyField);
		label(context, "Image Texture Id", imageField);
		context.drawTextWithShadow(textRenderer,
			Text.literal("Use a texture id like gexpress:textures/gui/gexpress_mini.png").formatted(Formatting.GRAY),
			imageField.getX(), imageField.getY() + imageField.getHeight() + 4, MUTED);

		int previewX = previewX();
		int previewW = previewW();
		if (previewW >= 320) {
			int previewY = outerY();
			int previewH = outerH();
			context.fill(previewX, previewY, previewX + previewW, previewY + previewH, PANEL);
			context.drawBorder(previewX, previewY, previewW, previewH, BORDER);
			context.fill(previewX, previewY, previewX + previewW, previewY + 3, GOLD);
			context.drawTextWithShadow(textRenderer, Text.literal("Live Preview").formatted(Formatting.BOLD),
				previewX + 18, previewY + 16, TEXT);
			int cardX = previewX + 18;
			int cardY = previewY + 42;
			GexpressTutorialScreen.renderPanel(context, textRenderer, Text.literal("G'Express Tutorial"),
				livePages(), page, cardX, cardY, previewW - 36, Math.max(220, previewH - 62));
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void resize(MinecraftClient client, int width, int height) {
		saveCurrentPage();
		super.resize(client, width, height);
		layout();
	}

	@Override
	public void close() {
		save();
		MinecraftClient.getInstance().setScreen(parent);
	}

	private TextFieldWidget field(String label, int maxLength) {
		TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 100, 18, Text.literal(label)));
		field.setMaxLength(maxLength);
		return field;
	}

	private void layout() {
		int x = outerX() + 18;
		int y = outerY() + 62;
		int fieldW = Math.max(220, formW() - 36);
		titleField.setDimensionsAndPosition(fieldW, 22, x, y);
		bodyField.setDimensionsAndPosition(fieldW, 34, x, y + 52);
		imageField.setDimensionsAndPosition(fieldW, 22, x, y + 114);
		int buttonY = y + 170;
		int buttonW = Math.min(112, Math.max(78, (fieldW - 24) / 4));
		previousButton.setDimensionsAndPosition(buttonW, 20, x, buttonY);
		nextButton.setDimensionsAndPosition(buttonW, 20, x + buttonW + 8, buttonY);
		addButton.setDimensionsAndPosition(buttonW, 20, x + (buttonW + 8) * 2, buttonY);
		deleteButton.setDimensionsAndPosition(buttonW, 20, x + (buttonW + 8) * 3, buttonY);
		saveButton.setDimensionsAndPosition(buttonW, 20, x, buttonY + 30);
		previewButton.setDimensionsAndPosition(buttonW, 20, x + buttonW + 8, buttonY + 30);
		resetSeenButton.setDimensionsAndPosition(buttonW, 20, x + (buttonW + 8) * 2, buttonY + 30);
		enabledButton.setDimensionsAndPosition(buttonW, 20, x + (buttonW + 8) * 3, buttonY + 30);
		if (doneButton != null) {
			doneButton.setDimensionsAndPosition(112, 20, width - 134, height - 32);
		}
	}

	private void label(DrawContext context, String label, TextFieldWidget field) {
		context.drawTextWithShadow(textRenderer, Text.literal(label), field.getX(), field.getY() - 12, MUTED);
	}

	private void loadPage() {
		GexpressTutorialStore.Page current = pages.get(MathHelper.clamp(page, 0, pages.size() - 1));
		titleField.setText(current.title());
		bodyField.setText(current.body());
		imageField.setText(current.image());
		updateButtons();
	}

	private void saveCurrentPage() {
		if (titleField == null || pages.isEmpty()) return;
		pages.set(MathHelper.clamp(page, 0, pages.size() - 1),
			new GexpressTutorialStore.Page(titleField.getText(), bodyField.getText(), imageField.getText()));
	}

	private List<GexpressTutorialStore.Page> livePages() {
		List<GexpressTutorialStore.Page> copy = new ArrayList<>(pages);
		if (titleField != null && !copy.isEmpty()) {
			copy.set(MathHelper.clamp(page, 0, copy.size() - 1),
				new GexpressTutorialStore.Page(titleField.getText(), bodyField.getText(), imageField.getText()));
		}
		return copy;
	}

	private void switchPage(int nextPage) {
		saveCurrentPage();
		page = MathHelper.clamp(nextPage, 0, pages.size() - 1);
		loadPage();
	}

	private void addPage() {
		saveCurrentPage();
		page = pages.size();
		pages.add(new GexpressTutorialStore.Page("New Page", "", ""));
		loadPage();
	}

	private void deletePage() {
		if (pages.size() <= 1) return;
		pages.remove(MathHelper.clamp(page, 0, pages.size() - 1));
		page = MathHelper.clamp(page, 0, pages.size() - 1);
		loadPage();
	}

	private void save() {
		saveCurrentPage();
		GexpressTutorialStore.setPages(pages);
	}

	private void updateButtons() {
		if (previousButton == null) return;
		previousButton.active = page > 0;
		nextButton.active = page < pages.size() - 1;
		deleteButton.active = pages.size() > 1;
	}

	private static Text enabledText() {
		return Text.literal(GexpressTutorialStore.enabled() ? "Enabled" : "Disabled");
	}

	private int outerX() {
		return 28;
	}

	private int outerY() {
		return 54;
	}

	private int outerH() {
		return Math.max(300, height - 100);
	}

	private int formW() {
		if (width < 900) return Math.max(320, width - 56);
		return MathHelper.clamp((width - 88) / 2, 360, 560);
	}

	private int previewX() {
		if (width < 900) return width + 1;
		return outerX() + formW() + 20;
	}

	private int previewW() {
		return Math.max(0, width - previewX() - 28);
	}
}
