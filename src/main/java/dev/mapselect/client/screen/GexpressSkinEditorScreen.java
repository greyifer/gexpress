package dev.mapselect.client.screen;

import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.client.render.DevWeaponModels;
import dev.mapselect.client.skin.BlockbenchSkinImporter;
import dev.mapselect.skin.WeaponSkinType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GexpressSkinEditorScreen extends Screen {
	private static final String[] CONTEXTS = {"firstperson_righthand", "firstperson_lefthand",
		"thirdperson_righthand", "thirdperson_lefthand", "gui", "ground", "fixed", "head"};
	private static final String[] LABELS = {"Rot X", "Rot Y", "Rot Z", "Pos X", "Pos Y", "Pos Z",
		"Scale X", "Scale Y", "Scale Z"};
	private static final int PANEL = 0xEE151B23;
	private static final int BORDER = 0x88677988;
	private static final int GOLD = 0xFFE7C66A;
	private static final int TEXT = 0xFFE8EDF2;
	private static final int MUTED = 0xFF9DA9B6;
	private static final int GREEN = 0xFF74D990;
	private static final int RED = 0xFFFF6B70;

	private final Screen parent;
	private final List<TextFieldWidget> values = new ArrayList<>();
	private List<BlockbenchSkinImporter.EditableSkin> skins = List.of();
	private WeaponSkinType selectedType = WeaponSkinType.GUN;
	private String selectedId = "";
	private int contextIndex;
	private int scroll;
	private TextFieldWidget nameField;
	private ButtonWidget knifeTab;
	private ButtonWidget gunTab;
	private ButtonWidget contextButton;
	private ButtonWidget applyButton;
	private ButtonWidget discardButton;
	private boolean loading;
	private boolean applying;
	private boolean dirty;
	private String status = "Select an imported skin.";
	private int statusColor = MUTED;

	public GexpressSkinEditorScreen(Screen parent) {
		super(Text.literal("Skin Model Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		loading = true;
		skins = BlockbenchSkinImporter.editableSkins();
		if (selectedId.isBlank()) selectFirst();
		nameField = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 200, 20, Text.literal("Display name")));
		nameField.setMaxLength(64);
		nameField.setChangedListener(value -> markDirty());
		values.clear();
		for (String label : LABELS) {
			TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 90, 20, Text.literal(label)));
			field.setMaxLength(16);
			field.setChangedListener(value -> markDirty());
			values.add(field);
		}
		knifeTab = addDrawableChild(ButtonWidget.builder(Text.literal("Knife Skins"), button -> selectType(WeaponSkinType.KNIFE)).build());
		gunTab = addDrawableChild(ButtonWidget.builder(Text.literal("Gun Skins"), button -> selectType(WeaponSkinType.GUN)).build());
		contextButton = addDrawableChild(ButtonWidget.builder(contextText(), button -> cycleContext()).build());
		applyButton = addDrawableChild(ButtonWidget.builder(Text.literal("Apply Now"), button -> apply()).build());
		discardButton = addDrawableChild(ButtonWidget.builder(Text.literal("Discard Changes"), button -> discardChanges()).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
			.dimensions(width - 116, height - 30, 96, 20).build());
		layoutWidgets();
		loadSelected();
		loading = false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xF00E1116);
		context.fill(0, 0, width, 44, 0xEE111820);
		context.fill(0, 42, width, 44, GOLD);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, TEXT);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.literal("Rename skins and edit their display transforms live."), width / 2, 25, MUTED);

		drawList(context, mouseX, mouseY);
		drawEditor(context);
		super.render(context, mouseX, mouseY, delta);
	}

	private void drawList(DrawContext context, int mouseX, int mouseY) {
		int x = 20;
		int y = 56;
		int w = listWidth();
		int h = height - 100;
		panel(context, x, y, w, h, selectedType.displayName() + " Skins");
		List<BlockbenchSkinImporter.EditableSkin> visible = visibleSkins();
		int top = y + 72;
		int bottom = y + h - 10;
		context.enableScissor(x + 1, top, x + w - 1, bottom);
		int rowY = top - scroll;
		for (BlockbenchSkinImporter.EditableSkin skin : visible) {
			if (rowY + 38 >= top && rowY <= bottom) {
				boolean selected = skin.id().equals(selectedId);
				boolean hovered = inside(mouseX, mouseY, x + 10, rowY, w - 20, 34);
				context.fill(x + 10, rowY, x + w - 10, rowY + 34,
					selected ? 0xAA344052 : hovered ? 0x77313A48 : 0x66212831);
				context.drawBorder(x + 10, rowY, w - 20, 34, selected ? GOLD : BORDER);
				context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(skin.displayName(), w - 34)),
					x + 16, rowY + 6, TEXT);
				context.drawTextWithShadow(textRenderer, Text.literal(skin.id()), x + 16, rowY + 20, MUTED);
			}
			rowY += 40;
		}
		context.disableScissor();
		if (visible.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.literal("No imported skins for this type."), x + 14, top + 8, MUTED);
		}
	}

	private void drawEditor(DrawContext context) {
		int x = editorX();
		int y = 56;
		int w = width - x - 20;
		int h = height - 100;
		panel(context, x, y, w, h, selectedId.isBlank() ? "No Skin Selected" : selectedId);
		context.drawTextWithShadow(textRenderer, Text.literal("Display Name"), nameField.getX(), nameField.getY() - 11, MUTED);
		context.drawTextWithShadow(textRenderer,
			Text.literal("Command id: " + (selectedId.isBlank() ? "-" : selectedId) + " (kept stable)"),
			x + 16, y + 76, MUTED);
		context.drawTextWithShadow(textRenderer, Text.literal("Display Context").formatted(Formatting.BOLD),
			x + 16, y + 104, TEXT);
		for (int i = 0; i < values.size(); i++) {
			context.drawTextWithShadow(textRenderer, Text.literal(LABELS[i]), values.get(i).getX(), values.get(i).getY() - 11, MUTED);
		}
		drawPreview(context, x, y, w, h);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), x + w / 2, y + h - 24, statusColor);
	}

	private void drawPreview(DrawContext context, int x, int y, int w, int h) {
		BlockbenchSkinImporter.EditableSkin skin = selectedSkin();
		if (skin == null) return;
		int previewX = x + w - Math.max(118, w / 4);
		int previewY = y + 146;
		int previewW = Math.max(96, x + w - previewX - 18);
		int previewH = Math.max(92, h - 192);
		context.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xAA0B1016);
		context.drawBorder(previewX, previewY, previewW, previewH, BORDER);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Live Skin Preview"), previewX + previewW / 2, previewY + 10, MUTED);
		ItemStack stack = skin.type() == WeaponSkinType.KNIFE ? WatheItems.KNIFE.getDefaultStack() : WatheItems.REVOLVER.getDefaultStack();
		NbtCompound data = new NbtCompound();
		data.putString(DevWeaponModels.SKIN_PREVIEW_KEY, skin.id());
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
		context.getMatrices().push();
		context.getMatrices().translate(previewX + previewW / 2.0F - 8.0F, previewY + previewH / 2.0F - 8.0F, 180.0F);
		float scale = Math.max(2.4F, Math.min(previewW, previewH) / 30.0F);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.drawItem(stack, 0, 0);
		context.getMatrices().pop();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && mouseX >= 30 && mouseX < 20 + listWidth() - 10) {
			int rowY = 128 - scroll;
			for (BlockbenchSkinImporter.EditableSkin skin : visibleSkins()) {
				if (inside(mouseX, mouseY, 30, rowY, listWidth() - 20, 34)) {
					if (dirty) {
						status = "Apply or discard the current changes before selecting another skin.";
						statusColor = RED;
						return true;
					}
					selectedId = skin.id();
					loadSelected();
					return true;
				}
				rowY += 40;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= 20 && mouseX < 20 + listWidth() && mouseY >= 56 && mouseY < height - 44) {
			int max = Math.max(0, visibleSkins().size() * 40 - (height - 182));
			scroll = MathHelper.clamp(scroll - (int) Math.round(verticalAmount * 24.0D), 0, max);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private void layoutWidgets() {
		int listW = listWidth();
		knifeTab.setDimensionsAndPosition((listW - 26) / 2, 20, 30, 88);
		gunTab.setDimensionsAndPosition((listW - 26) / 2, 20, 34 + (listW - 26) / 2, 88);
		int x = editorX() + 16;
		int y = 100;
		int editorW = width - editorX() - 36;
		nameField.setDimensionsAndPosition(Math.max(160, editorW - 16), 20, x, y);
		contextButton.setDimensionsAndPosition(Math.min(250, editorW / 2), 20, x, y + 70);
		int fieldAreaW = Math.max(240, editorW * 2 / 3);
		int gap = 10;
		int fieldW = Math.max(70, (fieldAreaW - gap * 2) / 3);
		for (int i = 0; i < values.size(); i++) {
			int col = i % 3;
			int row = i / 3;
			values.get(i).setDimensionsAndPosition(fieldW, 20, x + col * (fieldW + gap), y + 118 + row * 46);
		}
		applyButton.setDimensionsAndPosition(130, 20, x, y + 260);
		discardButton.setDimensionsAndPosition(130, 20, x + 140, y + 260);
	}

	private void panel(DrawContext context, int x, int y, int w, int h, String heading) {
		context.fill(x, y, x + w, y + h, PANEL);
		context.drawBorder(x, y, w, h, BORDER);
		context.fill(x, y, x + w, y + 2, GOLD);
		context.drawTextWithShadow(textRenderer, Text.literal(heading).formatted(Formatting.BOLD), x + 12, y + 12, TEXT);
	}

	private void selectType(WeaponSkinType type) {
		if (dirty) {
			status = "Apply or discard the current changes before switching skin type.";
			statusColor = RED;
			return;
		}
		selectedType = type;
		scroll = 0;
		selectFirst();
		loadSelected();
	}

	private void selectFirst() {
		selectedId = visibleSkins().stream().findFirst().map(BlockbenchSkinImporter.EditableSkin::id).orElse("");
	}

	private void cycleContext() {
		if (dirty) {
			status = "Apply the current values before switching display context.";
			statusColor = RED;
			return;
		}
		contextIndex = (contextIndex + 1) % CONTEXTS.length;
		contextButton.setMessage(contextText());
		loadTransform();
	}

	private Text contextText() {
		return Text.literal(CONTEXTS[contextIndex].replace('_', ' '));
	}

	private void loadSelected() {
		loading = true;
		dirty = false;
		BlockbenchSkinImporter.EditableSkin skin = selectedSkin();
		nameField.setText(skin == null ? "" : skin.displayName());
		loadTransform();
		status = skin == null ? "No editable skins found." : "Edit the values, then click Apply Now.";
		statusColor = MUTED;
		loading = false;
	}

	private void discardChanges() {
		loadSelected();
		status = "Changes discarded.";
		statusColor = MUTED;
	}

	private void loadTransform() {
		loading = true;
		BlockbenchSkinImporter.DisplayTransform transform = BlockbenchSkinImporter.displayTransform(
			selectedId, selectedType, CONTEXTS[contextIndex]);
		float[] data = {transform.rotationX(), transform.rotationY(), transform.rotationZ(), transform.translationX(),
			transform.translationY(), transform.translationZ(), transform.scaleX(), transform.scaleY(), transform.scaleZ()};
		for (int i = 0; i < values.size(); i++) values.get(i).setText(format(data[i]));
		loading = false;
	}

	private void markDirty() {
		if (!loading && !selectedId.isBlank()) {
			dirty = true;
			status = "Unsaved changes. Click Apply Now when ready.";
			statusColor = GOLD;
		}
	}

	private void apply() {
		if (applying || selectedId.isBlank()) return;
		float[] parsed = new float[9];
		for (int i = 0; i < parsed.length; i++) {
			try {
				parsed[i] = Float.parseFloat(values.get(i).getText().trim());
			} catch (NumberFormatException error) {
				status = LABELS[i] + " is not a number.";
				statusColor = RED;
				return;
			}
		}
		BlockbenchSkinImporter.DisplayTransform transform = new BlockbenchSkinImporter.DisplayTransform(
			parsed[0], parsed[1], parsed[2], parsed[3], parsed[4], parsed[5], parsed[6], parsed[7], parsed[8]);
		applying = true;
		applyButton.active = false;
		status = "Applying skin changes...";
		statusColor = GOLD;
		BlockbenchSkinImporter.saveSkinSettings(MinecraftClient.getInstance(), selectedId, selectedType, nameField.getText(),
			CONTEXTS[contextIndex], transform).whenComplete((result, error) -> MinecraftClient.getInstance().execute(() -> {
			applying = false;
			applyButton.active = true;
			if (error != null || result == null || result.fatalError() != null) {
				status = "Apply failed: " + (error != null ? error.getMessage() : result == null ? "unknown error" : result.fatalError());
				statusColor = RED;
				return;
			}
			dirty = false;
			skins = BlockbenchSkinImporter.editableSkins();
			status = "Applied. Menus and skin commands now use " + nameField.getText().strip() + ".";
			statusColor = GREEN;
		}));
	}

	private List<BlockbenchSkinImporter.EditableSkin> visibleSkins() {
		return skins.stream().filter(skin -> skin.type() == selectedType).toList();
	}

	private BlockbenchSkinImporter.EditableSkin selectedSkin() {
		return skins.stream().filter(skin -> skin.id().equals(selectedId) && skin.type() == selectedType)
			.findFirst().orElse(null);
	}

	private int listWidth() {
		return Math.min(300, Math.max(220, width / 4));
	}

	private int editorX() {
		return 36 + listWidth();
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private static String format(float value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
