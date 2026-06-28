package dev.mapselect.client.screen;

import dev.mapselect.config.GexpressConfig;
import dev.mapselect.skin.WeaponSkin;
import dev.mapselect.skin.WeaponSkinType;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class GexpressCaseEditorScreen extends Screen {
	private static final int CASE_ROW_HEIGHT = 40;
	private static final int REWARD_ROW_HEIGHT = 36;
	private static final int SKIN_ROW_HEIGHT = 36;
	private static final int PANEL = 0xCC151A20;
	private static final int PANEL_SOFT = 0x88242B34;
	private static final int FIELD_BG = 0xDD070A0E;
	private static final int BORDER = 0x775E6D7E;
	private static final int GOLD = 0xFFE7C66A;
	private static final int BLUE = 0xFF7FB6FF;
	private static final int GREEN = 0xFF74D990;
	private static final int RED = 0xFFFF7A7A;
	private static final int TEXT = 0xFFE8EDF2;
	private static final int MUTED = 0xFF9DA9B6;

	private final Screen parent;
	private final List<DraftReward> rewards = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private TextFieldWidget idField;
	private TextFieldWidget nameField;
	private TextFieldWidget priceField;
	private ButtonWidget saveButton;
	private ButtonWidget deleteButton;
	private ButtonWidget newButton;
	private ButtonWidget doneButton;
	private ButtonWidget addRewardButton;
	private ButtonWidget panelToggleButton;
	private String selectedId = "";
	private String status = "Select a case or create a new one.";
	private int statusColor = MUTED;
	private int caseScroll;
	private int rewardScroll;
	private int skinScroll;
	private WeaponSkinType selectedType = WeaponSkinType.GUN;
	private WeaponSkin selectedSkin;
	private boolean compactSkinLibrary;

	public GexpressCaseEditorScreen(Screen parent) {
		super(Text.literal("Case Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		hits.clear();
		idField = addField(0, 0, 120, 18, 48, "case_id");
		nameField = addField(0, 0, 160, 18, 80, "Display name");
		priceField = addField(0, 0, 64, 18, 8, "Price");
		addRewardButton = addDrawableChild(ButtonWidget.builder(Text.literal("Add Reward"), button -> addSelectedReward()).build());
		panelToggleButton = addDrawableChild(ButtonWidget.builder(Text.literal("Browse Skins"), button -> {
			compactSkinLibrary = !compactSkinLibrary;
			updatePanelButtons();
		}).build());
		saveButton = addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> save()).build());
		deleteButton = addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), button -> delete()).build());
		newButton = addDrawableChild(ButtonWidget.builder(Text.literal("New"), button -> clearForm()).build());
		doneButton = addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).build());
		layoutWidgets();
		ensureSelectedSkin();

		GexpressConfig.SkinCaseEntry selected = findSelected();
		if (selected != null) {
			load(selected);
		} else if (!entries().isEmpty()) {
			load(entries().get(0));
		} else {
			clearForm();
		}
	}

	private TextFieldWidget addField(int x, int y, int w, int h, int maxLength, String label) {
		TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, w, h, Text.literal(label)));
		field.setMaxLength(maxLength);
		return field;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		layoutWidgets();
		clampScrolls();
		hits.clear();
		context.fill(0, 0, width, height, 0xF00E1116);
		drawHeader(context);
		drawCaseList(context, mouseX, mouseY);
		drawEditor(context, mouseX, mouseY);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (Hit hit : List.copyOf(hits)) {
				if (inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
					hit.action.run();
					return true;
				}
			}
			if (clickCaseList(mouseX, mouseY)) return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (inside(mouseX, mouseY, listX(), listY(), listW(), listH())) {
			int max = Math.max(0, entries().size() * CASE_ROW_HEIGHT - (listH() - 38));
			caseScroll = MathHelper.clamp(caseScroll - (int) Math.round(verticalAmount * 24.0D), 0, max);
			return true;
		}
		if ((!compactLayout() || !compactSkinLibrary)
				&& inside(mouseX, mouseY, rewardX(), rewardY(), rewardW(), rewardH())) {
			rewardScroll = MathHelper.clamp(rewardScroll - (int) Math.round(verticalAmount * 24.0D), 0, maxRewardScroll());
			return true;
		}
		if ((!compactLayout() || compactSkinLibrary)
				&& inside(mouseX, mouseY, skinX(), skinY(), skinW(), skinH())) {
			skinScroll = MathHelper.clamp(skinScroll - (int) Math.round(verticalAmount * 24.0D), 0, maxSkinScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	private void drawHeader(DrawContext context) {
		context.fill(0, 0, width, 42, 0xEE111820);
		context.fill(0, 40, width, 42, GOLD);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 11, TEXT);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.literal("Cases, prices, and weighted knife/gun drops").formatted(Formatting.GRAY),
			width / 2, 24, MUTED);
	}

	private void drawCaseList(DrawContext context, int mouseX, int mouseY) {
		int x = listX();
		int y = listY();
		int w = listW();
		int h = listH();
		panel(context, x, y, w, h, "Cases");
		List<GexpressConfig.SkinCaseEntry> entries = entries();
		if (entries.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.literal("No cases yet."), x + 12, y + 38, MUTED);
			return;
		}
		int listTop = y + 34;
		int rowY = listTop - caseScroll;
		for (GexpressConfig.SkinCaseEntry entry : entries) {
			if (rowY + 34 >= listTop && rowY <= y + h - 10) {
				boolean selected = entry.id().equals(selectedId);
				boolean hovered = inside(mouseX, mouseY, x + 10, rowY, w - 20, 34);
				context.fill(x + 10, rowY, x + w - 10, rowY + 34,
					selected ? 0xAA344052 : hovered ? 0x77313A48 : PANEL_SOFT);
				context.drawBorder(x + 10, rowY, w - 20, 34, selected ? GOLD : BORDER);
				context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(entry.displayName(), w - 34)),
					x + 16, rowY + 5, TEXT);
				context.drawTextWithShadow(textRenderer,
					Text.literal(entry.price() + " G'Coins  |  " + entry.rewards().size() + " rewards"),
					x + 16, rowY + 19, MUTED);
			}
			rowY += CASE_ROW_HEIGHT;
		}
	}

	private boolean clickCaseList(double mouseX, double mouseY) {
		int x = listX();
		int rowY = listY() + 34 - caseScroll;
		for (GexpressConfig.SkinCaseEntry entry : entries()) {
			if (inside(mouseX, mouseY, x + 10, rowY, listW() - 20, 34)) {
				load(entry);
				return true;
			}
			rowY += CASE_ROW_HEIGHT;
		}
		return false;
	}

	private void drawEditor(DrawContext context, int mouseX, int mouseY) {
		int x = editorX();
		int y = listY();
		int w = editorW();
		int h = listH();
		panel(context, x, y, w, h, selectedId.isEmpty() ? "New Case" : "Editing: " + selectedId);

		context.drawTextWithShadow(textRenderer, Text.literal("Id"), idField.getX(), idField.getY() - 11, MUTED);
		context.drawTextWithShadow(textRenderer, Text.literal("Display Name"), nameField.getX(), nameField.getY() - 11, MUTED);
		context.drawTextWithShadow(textRenderer, Text.literal("Price"), priceField.getX(), priceField.getY() - 11, MUTED);
		context.drawTextWithShadow(textRenderer, Text.literal(status), x + 14, y + 84, statusColor);
		if (!compactLayout() || !compactSkinLibrary) drawRewardPanel(context, mouseX, mouseY);
		if (!compactLayout() || compactSkinLibrary) drawSkinPanel(context, mouseX, mouseY);
	}

	private void drawRewardPanel(DrawContext context, int mouseX, int mouseY) {
		int x = rewardX();
		int y = rewardY();
		int w = rewardW();
		int h = rewardH();
		context.fill(x, y, x + w, y + h, 0xAA10161E);
		context.drawBorder(x, y, w, h, BORDER);
		context.drawTextWithShadow(textRenderer, Text.literal("Rewards").formatted(Formatting.BOLD), x + 10, y + 9, TEXT);
		context.drawTextWithShadow(textRenderer, Text.literal(rewards.size() + " selected"), x + 100, y + 10, MUTED);
		context.drawTextWithShadow(textRenderer, Text.literal("Type"), x + 18, y + 28, MUTED);
		context.drawTextWithShadow(textRenderer, Text.literal("Skin"), x + 90, y + 28, MUTED);
		context.drawTextWithShadow(textRenderer, Text.literal("Weight"), x + w - 282, y + 28, MUTED);

		int top = y + 44;
		int bottom = y + h - 8;
		if (rewards.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.literal("Choose skins from the right panel."), x + 12, top + 10, MUTED);
			return;
		}

		context.enableScissor(x + 6, top, x + w - 6, bottom);
		int rowY = top - rewardScroll;
		for (int i = 0; i < rewards.size(); i++) {
			DraftReward reward = rewards.get(i);
			if (rowY + 30 >= top && rowY <= bottom) {
				boolean selected = reward.type == selectedType && reward.skin == selectedSkin;
				context.fill(x + 8, rowY, x + w - 8, rowY + 30, selected ? 0x66344652 : 0x44242B34);
				context.drawBorder(x + 8, rowY, w - 16, 30, selected ? GOLD : 0x335E6D7E);
				context.fill(x + 10, rowY + 2, x + 14, rowY + 28, 0xFF000000 | reward.skin.color());
				context.drawTextWithShadow(textRenderer, Text.literal(reward.type.displayName()), x + 20, rowY + 5, MUTED);
				int removeW = 60;
				int removeX = x + w - removeW - 12;
				int controlsX = removeX - 200;
				int nameW = Math.max(40, controlsX - (x + 90) - 10);
				context.drawTextWithShadow(textRenderer,
					Text.literal(textRenderer.trimToWidth(reward.skin.displayName(), nameW)),
					x + 90, rowY + 5, TEXT);
				context.drawTextWithShadow(textRenderer, Text.literal(reward.skin.id()), x + 90, rowY + 17, MUTED);
				int rewardIndex = i;
				drawHitButton(context, controlsX, rowY + 6, 30, 18, "-10", mouseX, mouseY,
					() -> changeWeight(rewardIndex, -10));
				drawHitButton(context, controlsX + 34, rowY + 6, 26, 18, "-1", mouseX, mouseY,
					() -> changeWeight(rewardIndex, -1));
				context.fill(controlsX + 64, rowY + 6, controlsX + 120, rowY + 24, FIELD_BG);
				context.drawBorder(controlsX + 64, rowY + 6, 56, 18, BORDER);
				context.drawCenteredTextWithShadow(textRenderer, Text.literal(Integer.toString(reward.weight)),
					controlsX + 92, rowY + 11, TEXT);
				drawHitButton(context, controlsX + 124, rowY + 6, 26, 18, "+1", mouseX, mouseY,
					() -> changeWeight(rewardIndex, 1));
				drawHitButton(context, controlsX + 154, rowY + 6, 30, 18, "+10", mouseX, mouseY,
					() -> changeWeight(rewardIndex, 10));
				drawHitButton(context, removeX, rowY + 6, removeW, 18, "Remove", mouseX, mouseY,
					() -> removeReward(rewardIndex));
			}
			rowY += REWARD_ROW_HEIGHT;
		}
		context.disableScissor();
	}

	private void drawSkinPanel(DrawContext context, int mouseX, int mouseY) {
		int x = skinX();
		int y = skinY();
		int w = skinW();
		int h = skinH();
		context.fill(x, y, x + w, y + h, 0xAA10161E);
		context.drawBorder(x, y, w, h, BORDER);
		context.drawTextWithShadow(textRenderer, Text.literal("Available Skins").formatted(Formatting.BOLD), x + 10, y + 9, TEXT);
		context.drawTextWithShadow(textRenderer, Text.literal("Click a skin to add or remove it."), x + 10, y + 23, MUTED);

		int tabX = x + 10;
		for (WeaponSkinType type : WeaponSkinType.values()) {
			boolean selected = type == selectedType;
			int tabW = Math.max(66, textRenderer.getWidth(type.displayName()) + 20);
			drawHitButton(context, tabX, y + 42, tabW, 20, type.displayName(), mouseX, mouseY,
				() -> selectType(type), selected);
			tabX += tabW + 6;
		}

		List<WeaponSkin> skins = availableSkins();
		int top = y + 70;
		int bottom = y + h - 8;
		if (skins.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.literal("No skins registered for this type."), x + 12, top + 10, MUTED);
			return;
		}

		context.enableScissor(x + 6, top, x + w - 6, bottom);
		int rowY = top - skinScroll;
		for (WeaponSkin skin : skins) {
			if (rowY + 30 >= top && rowY <= bottom) {
				boolean selected = skin == selectedSkin;
				boolean added = findReward(selectedType, skin) >= 0;
				int bg = selected ? 0x66344652 : added ? 0x55354D38 : 0x44242B34;
				int border = selected ? GOLD : added ? GREEN : 0x335E6D7E;
				context.fill(x + 8, rowY, x + w - 8, rowY + 30, bg);
				context.drawBorder(x + 8, rowY, w - 16, 30, border);
				context.fill(x + 10, rowY + 2, x + 18, rowY + 28, 0xFF000000 | skin.color());
				context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(skin.displayName(), w - 86)),
					x + 26, rowY + 5, TEXT);
				context.drawTextWithShadow(textRenderer, Text.literal(selectedType.id() + ":" + skin.logical(selectedType).id()),
					x + 26, rowY + 17, MUTED);
				context.drawTextWithShadow(textRenderer, Text.literal(added ? "Added" : "Add"), x + w - 58, rowY + 10,
					added ? GREEN : BLUE);
				hits.add(new Hit(x + 8, rowY, w - 16, 30, () -> toggleReward(selectedType, skin)));
			}
			rowY += SKIN_ROW_HEIGHT;
		}
		context.disableScissor();
	}

	private void panel(DrawContext context, int x, int y, int w, int h, String title) {
		context.fill(x, y, x + w, y + h, PANEL);
		context.drawBorder(x, y, w, h, BORDER);
		context.fill(x, y, x + w, y + 2, GOLD);
		context.drawTextWithShadow(textRenderer, Text.literal(title).formatted(Formatting.BOLD), x + 12, y + 11, TEXT);
	}

	private void drawHitButton(DrawContext context, int x, int y, int w, int h, String label, int mouseX, int mouseY,
			Runnable action) {
		drawHitButton(context, x, y, w, h, label, mouseX, mouseY, action, false);
	}

	private void drawHitButton(DrawContext context, int x, int y, int w, int h, String label, int mouseX, int mouseY,
			Runnable action, boolean selected) {
		boolean hovered = inside(mouseX, mouseY, x, y, w, h);
		context.fill(x, y, x + w, y + h, selected ? 0xAA344052 : hovered ? 0x99404B5C : 0x88313A48);
		context.drawBorder(x, y, w, h, selected ? GOLD : BORDER);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + w / 2, y + (h - 8) / 2,
			selected ? GOLD : TEXT);
		hits.add(new Hit(x, y, w, h, action));
	}

	private void layoutWidgets() {
		if (idField == null) return;
		int x = editorX() + 14;
		int y = listY() + 44;
		int w = editorW() - 28;
		int priceW = 74;
		int idW = Math.min(170, Math.max(112, w / 5));
		int nameW = Math.max(140, w - idW - priceW - 24);
		idField.setDimensionsAndPosition(idW, 18, x, y);
		nameField.setDimensionsAndPosition(nameW, 18, x + idW + 10, y);
		priceField.setDimensionsAndPosition(priceW, 18, x + idW + nameW + 20, y);

		addRewardButton.setDimensionsAndPosition(Math.min(112, rewardW() - 22), 20,
			rewardX() + rewardW() - Math.min(112, rewardW() - 22) - 10, rewardY() + 8);
		panelToggleButton.setDimensionsAndPosition(104, 20, rewardX(), rewardY() - 24);
		updatePanelButtons();
		updateAddButton();

		int buttonY = height - 32;
		saveButton.setDimensionsAndPosition(78, 20, editorX() + 14, buttonY);
		deleteButton.setDimensionsAndPosition(78, 20, editorX() + 98, buttonY);
		newButton.setDimensionsAndPosition(78, 20, editorX() + 182, buttonY);
		doneButton.setDimensionsAndPosition(90, 20, width - 114, buttonY);
	}

	private void updateAddButton() {
		if (addRewardButton == null) return;
		ensureSelectedSkin();
		if (selectedSkin == null) {
			addRewardButton.active = false;
			addRewardButton.setMessage(Text.literal("No Skin"));
			return;
		}
		addRewardButton.active = true;
		addRewardButton.setMessage(Text.literal("Add " + textRenderer.trimToWidth(selectedSkin.displayName(), 58)));
	}

	private void updatePanelButtons() {
		if (panelToggleButton == null || addRewardButton == null) return;
		panelToggleButton.visible = compactLayout();
		panelToggleButton.setMessage(Text.literal(compactSkinLibrary ? "Show Rewards" : "Browse Skins"));
		addRewardButton.visible = !compactLayout() || !compactSkinLibrary;
	}

	private void load(GexpressConfig.SkinCaseEntry entry) {
		selectedId = entry.id();
		idField.setText(entry.id());
		nameField.setText(entry.displayName());
		priceField.setText(Integer.toString(entry.price()));
		rewards.clear();
		for (GexpressConfig.SkinCaseReward reward : entry.rewards()) {
			if (reward.configured()) {
				rewards.add(new DraftReward(reward.type(), reward.skin().logical(reward.type()), reward.weight()));
			}
		}
		rewardScroll = 0;
		skinScroll = 0;
		status = "Loaded " + entry.displayName() + ".";
		statusColor = MUTED;
	}

	private void clearForm() {
		selectedId = "";
		idField.setText("");
		nameField.setText("");
		priceField.setText("100");
		rewards.clear();
		rewardScroll = 0;
		skinScroll = 0;
		status = "New case draft.";
		statusColor = BLUE;
	}

	private void save() {
		String id = normalizeId(idField.getText());
		String name = clean(nameField.getText());
		int price = parseInt(priceField.getText(), 100);
		if (id.isEmpty() || name.isEmpty()) {
			status = "Id and display name are required.";
			statusColor = RED;
			return;
		}
		if (rewards.isEmpty()) {
			status = "Add at least one reward.";
			statusColor = RED;
			return;
		}
		List<String> encodedRewards = new ArrayList<>();
		for (DraftReward reward : rewards) {
			if (reward.type == null || reward.skin == null || !reward.skin.supports(reward.type) || reward.weight <= 0) {
				status = "A reward has invalid data.";
				statusColor = RED;
				return;
			}
			encodedRewards.add(reward.type.id() + ":" + reward.skin.logical(reward.type).id() + ":" + reward.weight);
		}
		List<String> rows = new ArrayList<>(GexpressConfig.getSkinCaseRows());
		rows.removeIf(row -> row.startsWith(id + "|") || (!selectedId.isEmpty() && row.startsWith(selectedId + "|")));
		rows.add(id + "|" + name + "|" + Math.max(0, price) + "|" + String.join(";;", encodedRewards));
		GexpressConfig.setSkinCaseRows(rows);
		GexpressOptionsScreen.pushGexpressConfigToServer();
		selectedId = id;
		idField.setText(id);
		status = "Saved " + name + ".";
		statusColor = GREEN;
	}

	private void delete() {
		String id = normalizeId(idField.getText());
		if (id.isEmpty()) {
			status = "Select a case first.";
			statusColor = RED;
			return;
		}
		List<String> rows = new ArrayList<>(GexpressConfig.getSkinCaseRows());
		boolean removed = rows.removeIf(row -> row.startsWith(id + "|"));
		GexpressConfig.setSkinCaseRows(rows);
		GexpressOptionsScreen.pushGexpressConfigToServer();
		clearForm();
		status = removed ? "Deleted " + id + "." : "No saved case matched " + id + ".";
		statusColor = removed ? GREEN : GOLD;
	}

	private void addSelectedReward() {
		ensureSelectedSkin();
		if (selectedSkin == null) {
			status = "Select a skin first.";
			statusColor = RED;
			return;
		}
		addReward(selectedType, selectedSkin);
	}

	private void toggleReward(WeaponSkinType type, WeaponSkin skin) {
		selectedType = type;
		selectedSkin = skin;
		int index = findReward(type, skin);
		if (index >= 0) {
			rewards.remove(index);
			status = "Removed " + skin.displayName() + ".";
			statusColor = GOLD;
		} else {
			addReward(type, skin);
		}
		clampScrolls();
		updateAddButton();
	}

	private void addReward(WeaponSkinType type, WeaponSkin skin) {
		if (type == null || skin == null || skin == WeaponSkin.DEFAULT || !skin.visibleInPicker(type)) {
			status = "That skin cannot be used for this drop type.";
			statusColor = RED;
			return;
		}
		if (findReward(type, skin) >= 0) {
			status = skin.displayName() + " is already in this case.";
			statusColor = GOLD;
			return;
		}
		rewards.add(new DraftReward(type, skin.logical(type), 100));
		status = "Added " + skin.displayName() + ".";
		statusColor = GREEN;
		clampScrolls();
	}

	private void removeReward(int index) {
		if (index < 0 || index >= rewards.size()) return;
		DraftReward removed = rewards.remove(index);
		status = "Removed " + removed.skin.displayName() + ".";
		statusColor = GOLD;
		clampScrolls();
	}

	private void changeWeight(int index, int delta) {
		if (index < 0 || index >= rewards.size()) return;
		DraftReward reward = rewards.get(index);
		reward.weight = MathHelper.clamp(reward.weight + delta, 1, 100000);
		status = reward.skin.displayName() + " weight: " + reward.weight + ".";
		statusColor = MUTED;
	}

	private void selectType(WeaponSkinType type) {
		selectedType = type;
		if (selectedSkin == null || !selectedSkin.visibleInPicker(selectedType)) {
			selectedSkin = firstSkin(type);
		}
		skinScroll = 0;
		updateAddButton();
	}

	private int findReward(WeaponSkinType type, WeaponSkin skin) {
		if (type == null || skin == null) return -1;
		WeaponSkin logical = skin.logical(type);
		for (int i = 0; i < rewards.size(); i++) {
			DraftReward reward = rewards.get(i);
			if (reward.type == type && reward.skin.logical(type) == logical) return i;
		}
		return -1;
	}

	private void ensureSelectedSkin() {
		if (selectedSkin == null || !selectedSkin.visibleInPicker(selectedType)) {
			selectedSkin = firstSkin(selectedType);
		}
	}

	private WeaponSkin firstSkin(WeaponSkinType type) {
		for (WeaponSkin skin : WeaponSkin.values()) {
			if (skin != WeaponSkin.DEFAULT && skin.visibleInPicker(type)) return skin;
		}
		return null;
	}

	private List<WeaponSkin> availableSkins() {
		List<WeaponSkin> skins = new ArrayList<>();
		for (WeaponSkin skin : WeaponSkin.values()) {
			if (skin != WeaponSkin.DEFAULT && skin.visibleInPicker(selectedType)) skins.add(skin);
		}
		return skins;
	}

	private List<GexpressConfig.SkinCaseEntry> entries() {
		List<GexpressConfig.SkinCaseEntry> entries = new ArrayList<>(GexpressConfig.getSkinCaseEntries());
		entries.sort(Comparator.comparing(GexpressConfig.SkinCaseEntry::displayName));
		return entries;
	}

	private GexpressConfig.SkinCaseEntry findSelected() {
		if (selectedId.isEmpty()) return null;
		for (GexpressConfig.SkinCaseEntry entry : entries()) {
			if (entry.id().equals(selectedId)) return entry;
		}
		return null;
	}

	private void clampScrolls() {
		rewardScroll = MathHelper.clamp(rewardScroll, 0, maxRewardScroll());
		skinScroll = MathHelper.clamp(skinScroll, 0, maxSkinScroll());
		caseScroll = MathHelper.clamp(caseScroll, 0, Math.max(0, entries().size() * CASE_ROW_HEIGHT - (listH() - 38)));
	}

	private int maxRewardScroll() {
		return Math.max(0, rewards.size() * REWARD_ROW_HEIGHT - (rewardH() - 52));
	}

	private int maxSkinScroll() {
		return Math.max(0, availableSkins().size() * SKIN_ROW_HEIGHT - (skinH() - 78));
	}

	private int listX() {
		return 22;
	}

	private int listY() {
		return 54;
	}

	private int listW() {
		return Math.min(282, Math.max(220, width / 4));
	}

	private int listH() {
		return Math.max(170, height - listY() - 54);
	}

	private int editorX() {
		return listX() + listW() + 16;
	}

	private int editorW() {
		return Math.max(440, width - editorX() - 22);
	}

	private int rewardX() {
		return editorX() + 14;
	}

	private int rewardY() {
		return listY() + 112;
	}

	private int rewardW() {
		if (!splitPanels()) return editorW() - 28;
		return Math.max(420, (editorW() - 40) * 58 / 100);
	}

	private int rewardH() {
		if (splitPanels()) return Math.max(148, height - rewardY() - 90);
		return Math.max(148, height - rewardY() - 90);
	}

	private int skinX() {
		return splitPanels() ? rewardX() + rewardW() + 12 : rewardX();
	}

	private int skinY() {
		return splitPanels() ? rewardY() : rewardY();
	}

	private int skinW() {
		return splitPanels() ? Math.max(230, editorW() - 28 - rewardW() - 12) : editorW() - 28;
	}

	private int skinH() {
		return rewardH();
	}

	private boolean splitPanels() {
		return editorW() >= 760;
	}

	private boolean compactLayout() {
		return !splitPanels();
	}

	private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private int parseInt(String raw, int fallback) {
		try {
			return Math.max(0, Integer.parseInt(raw == null ? "" : raw.trim()));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private String normalizeId(String raw) {
		if (raw == null) return "";
		return raw.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
	}

	private String clean(String raw) {
		return raw == null ? "" : raw.replace("|", "").strip();
	}

	private record Hit(int x, int y, int w, int h, Runnable action) {}

	private static final class DraftReward {
		private final WeaponSkinType type;
		private final WeaponSkin skin;
		private int weight;

		private DraftReward(WeaponSkinType type, WeaponSkin skin, int weight) {
			this.type = type;
			this.skin = skin;
			this.weight = MathHelper.clamp(weight, 1, 100000);
		}
	}
}
