package dev.mapselect.client.screen;

import dev.mapselect.config.GexpressConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GexpressLevelRewardEditorScreen extends Screen {
	private final Screen parent;
	private TextFieldWidget levelField;
	private TextFieldWidget rewardOneField;
	private TextFieldWidget rewardTwoField;
	private TextFieldWidget rewardThreeField;
	private TextFieldWidget descriptionField;
	private TextFieldWidget commandOneField;
	private TextFieldWidget commandTwoField;
	private TextFieldWidget commandThreeField;
	private int selectedLevel;
	private int listScroll;
	private static final int LIST_ROW_HEIGHT = 25;

	public GexpressLevelRewardEditorScreen(Screen parent) {
		super(Text.translatable("gui.gexpress.level_reward_editor.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int formX = formX();
		int formY = 54;
		int formWidth = Math.max(220, width - formX - 28);
		levelField = field(formX, formY, 72, 18, 8, "gui.gexpress.level_reward_editor.level");
		int rewardWidth = Math.min(360, formWidth);
		int longWidth = Math.min(520, formWidth);
		rewardOneField = field(formX, formY + 35, rewardWidth, 18, 96, "gui.gexpress.level_reward_editor.reward_one");
		rewardTwoField = field(formX, formY + 63, rewardWidth, 18, 96, "gui.gexpress.level_reward_editor.reward_two");
		rewardThreeField = field(formX, formY + 91, rewardWidth, 18, 96, "gui.gexpress.level_reward_editor.reward_three");
		descriptionField = field(formX, formY + 126, longWidth, 18, 240, "gui.gexpress.level_reward_editor.description");
		commandOneField = field(formX, formY + 161, longWidth, 18, 512, "gui.gexpress.level_reward_editor.command_one");
		commandTwoField = field(formX, formY + 189, longWidth, 18, 512, "gui.gexpress.level_reward_editor.command_two");
		commandThreeField = field(formX, formY + 217, longWidth, 18, 512, "gui.gexpress.level_reward_editor.command_three");

		int buttonY = Math.min(height - 58, formY + 258);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.level_reward_editor.save"), button -> save())
			.dimensions(formX, buttonY, 72, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.level_reward_editor.delete"), button -> delete())
			.dimensions(formX + 80, buttonY, 72, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.level_reward_editor.new"), button -> clearForm())
			.dimensions(formX + 160, buttonY, 72, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
			.dimensions(width / 2 - 45, height - 28, 90, 20)
			.build());
		clearForm();
	}

	private TextFieldWidget field(int x, int y, int w, int h, int maxLength, String key) {
		TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, w, h, Text.translatable(key)));
		field.setMaxLength(maxLength);
		return field;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xAA11151B);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.translatable("gui.gexpress.level_reward_editor.subtitle").formatted(Formatting.GRAY),
			width / 2, 29, 0xFF9BA3AE);
		drawRewardList(context, mouseX, mouseY);
		drawLabels(context);
		drawHelp(context);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && clickRewardList(mouseX, mouseY)) return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= 18 && mouseX < listRight() && mouseY >= 52 && mouseY < listBottom()) {
			listScroll = Math.max(0, Math.min(maxListScroll(), listScroll - (int) Math.signum(verticalAmount) * LIST_ROW_HEIGHT));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	private void drawRewardList(DrawContext context, int mouseX, int mouseY) {
		int x = 18;
		int y = 52;
		int listWidth = listRight() - x;
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.level_reward_editor.rewards"),
			x, y - 14, 0xFFFFFFFF);
		context.enableScissor(x, y, listRight(), listBottom());
		int row = 0;
		for (GexpressConfig.LevelRoadmapEntry entry : entries()) {
			int ry = y + row * LIST_ROW_HEIGHT - listScroll;
			if (ry + 21 <= y || ry >= listBottom()) {
				row++;
				continue;
			}
			boolean selected = entry.level() == selectedLevel;
			boolean hovered = mouseX >= x && mouseX < listRight() && mouseY >= ry && mouseY < ry + 21;
			context.fill(x, ry, listRight(), ry + 21, selected ? 0xAA2D3542 : hovered ? 0x77313A48 : 0x55212833);
			context.drawBorder(x, ry, listWidth, 21, selected ? 0xAAE0B65A : 0x443C4A58);
			context.drawTextWithShadow(textRenderer, Text.literal("Level " + entry.level()), x + 5, ry + 3,
				0xFFFFFFFF);
			String reward = entry.rewardTitles().isEmpty()
				? entry.title()
				: String.join(" + ", entry.rewardTitles());
			context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(reward, listWidth - 12)),
				x + 5, ry + 12, 0xFFFFD57A);
			row++;
		}
		context.disableScissor();
		if (maxListScroll() > 0) {
			int trackX = listRight() + 3;
			int trackH = listBottom() - y;
			int thumbH = Math.max(18, trackH * trackH / Math.max(trackH, entries().size() * LIST_ROW_HEIGHT));
			int thumbY = y + (trackH - thumbH) * listScroll / maxListScroll();
			context.fill(trackX, y, trackX + 2, listBottom(), 0x553C4A58);
			context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFFBFA35A);
		}
	}

	private boolean clickRewardList(double mouseX, double mouseY) {
		int x = 18;
		int y = 52;
		if (mouseX < x || mouseX >= listRight() || mouseY < y || mouseY >= listBottom()) return false;
		int row = 0;
		for (GexpressConfig.LevelRoadmapEntry entry : entries()) {
			int ry = y + row * LIST_ROW_HEIGHT - listScroll;
			if (mouseY >= ry && mouseY < ry + 21) {
				load(entry);
				return true;
			}
			row++;
		}
		return false;
	}

	private void drawLabels(DrawContext context) {
		int x = formX();
		int y = 43;
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.level_reward_editor.level"), x, y, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.level_reward_editor.reward_one"), x, y + 35, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.level_reward_editor.reward_two"), x, y + 63, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.level_reward_editor.reward_three"), x, y + 91, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.level_reward_editor.description"), x, y + 126, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.level_reward_editor.command_one"), x, y + 161, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.level_reward_editor.command_two"), x, y + 189, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.level_reward_editor.command_three"), x, y + 217, 0xFFB9C2CE);
	}

	private void drawHelp(DrawContext context) {
		int x = formX();
		int y = Math.min(height - 84, 340);
		context.drawTextWithShadow(textRenderer,
			Text.translatable("gui.gexpress.level_reward_editor.help_1").formatted(Formatting.GRAY),
			x, y, 0xFF9BA3AE);
		context.drawTextWithShadow(textRenderer,
			Text.translatable("gui.gexpress.level_reward_editor.help_2").formatted(Formatting.GRAY),
			x, y + 12, 0xFF9BA3AE);
	}

	private void load(GexpressConfig.LevelRoadmapEntry entry) {
		selectedLevel = entry.level();
		levelField.setText(Integer.toString(entry.level()));
		List<String> rewards = entry.rewardTitles();
		rewardOneField.setText(rewards.size() > 0 ? rewards.get(0) : entry.title());
		rewardTwoField.setText(rewards.size() > 1 ? rewards.get(1) : "");
		rewardThreeField.setText(rewards.size() > 2 ? rewards.get(2) : "");
		descriptionField.setText(entry.description());
		List<String> commands = entry.commands();
		commandOneField.setText(commands.size() > 0 ? commands.get(0) : "");
		commandTwoField.setText(commands.size() > 1 ? commands.get(1) : "");
		commandThreeField.setText(commands.size() > 2 ? commands.get(2) : "");
	}

	private void clearForm() {
		selectedLevel = 0;
		levelField.setText("");
		rewardOneField.setText("");
		rewardTwoField.setText("");
		rewardThreeField.setText("");
		descriptionField.setText("");
		commandOneField.setText("");
		commandTwoField.setText("");
		commandThreeField.setText("");
	}

	private void save() {
		int level = parseLevel(levelField.getText());
		if (level <= 0) return;
		List<String> rewards = nonBlank(rewardOneField.getText(), rewardTwoField.getText(), rewardThreeField.getText());
		if (rewards.isEmpty()) return;
		List<String> commands = nonBlank(commandOneField.getText(), commandTwoField.getText(), commandThreeField.getText());
		List<String> rows = new ArrayList<>(GexpressConfig.getLevelRewardRoadmapStrings());
		rows.removeIf(row -> {
			GexpressConfig.LevelRoadmapEntry entry = parseReward(row);
			return entry != null && entry.level() == level;
		});
		rows.add(level + "|" + joinSafe(rewards) + "|" + clean(descriptionField.getText()) + "|" + joinSafe(commands));
		GexpressConfig.setLevelRewardRoadmapStrings(rows);
		GexpressOptionsScreen.pushGexpressConfigToServer();
		selectedLevel = level;
	}

	private void delete() {
		int level = parseLevel(levelField.getText());
		if (level <= 0) return;
		List<String> rows = new ArrayList<>(GexpressConfig.getLevelRewardRoadmapStrings());
		rows.removeIf(row -> {
			GexpressConfig.LevelRoadmapEntry entry = parseReward(row);
			return entry != null && entry.level() == level;
		});
		GexpressConfig.setLevelRewardRoadmapStrings(rows);
		GexpressOptionsScreen.pushGexpressConfigToServer();
		clearForm();
	}

	private List<GexpressConfig.LevelRoadmapEntry> entries() {
		List<GexpressConfig.LevelRoadmapEntry> entries = new ArrayList<>(GexpressConfig.getLevelRoadmapEntries());
		entries.sort(Comparator.comparingInt(GexpressConfig.LevelRoadmapEntry::level));
		return entries;
	}

	private GexpressConfig.LevelRoadmapEntry parseReward(String row) {
		if (row == null || row.isBlank()) return null;
		String[] parts = row.split("\\|", 4);
		int level = parseLevel(parts.length > 0 ? parts[0] : "");
		if (level <= 0 || parts.length < 2) return null;
		String title = parts[1].strip();
		String description = parts.length >= 3 ? parts[2].strip() : "";
		String command = parts.length >= 4 ? parts[3].strip() : "";
		return new GexpressConfig.LevelRoadmapEntry(level, title, description, command);
	}

	private int parseLevel(String raw) {
		try {
			return Math.max(0, Math.min(999, Integer.parseInt(raw == null ? "" : raw.trim())));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private List<String> nonBlank(String... values) {
		List<String> out = new ArrayList<>();
		for (String value : values) {
			String cleaned = clean(value);
			if (!cleaned.isEmpty()) out.add(cleaned);
		}
		return out;
	}

	private String joinSafe(List<String> values) {
		return String.join(";;", values.stream().map(this::clean).filter(value -> !value.isEmpty()).toList());
	}

	private String clean(String raw) {
		return raw == null ? "" : raw.replace("|", "").strip();
	}

	private int formX() {
		return Math.max(210, Math.min(330, width / 3));
	}

	private int listRight() {
		return formX() - 22;
	}

	private int listBottom() {
		return Math.max(80, height - 42);
	}

	private int maxListScroll() {
		return Math.max(0, entries().size() * LIST_ROW_HEIGHT - (listBottom() - 52));
	}
}
