package dev.mapselect.client.screen;

import dev.mapselect.client.preset.ClientPresetCache;
import dev.mapselect.client.preset.ClientTrainPresetCache;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.train.TrainPreset;
import io.github.caspe.glib.api.client.editor.GlibWorkbenchScreen;
import io.github.caspe.glib.api.client.ui.GlibButton;
import io.github.caspe.glib.api.client.ui.GlibRect;
import io.github.caspe.glib.api.client.ui.GlibScrollableRegion;
import io.github.caspe.glib.api.client.ui.GlibTextField;
import io.github.caspe.glib.api.client.ui.GlibUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Direct train-cart workbench with no list-option or intermediary configuration pages. */
final class GexpressTrainCartEditorScreen extends GlibWorkbenchScreen {
	private static final int MUTED = 0xFF9EA7B3;
	private static final int ROW = 0xC019222E;
	private static final int ROW_HOVER = 0xE01D2835;
	private static final int ROW_HEIGHT = 44;

	private final String mapName;
	private final String trainName;
	private final TrainPreset edit;
	private final GlibScrollableRegion scroll = new GlibScrollableRegion().scrollStep(36);
	private GlibTextField coordinates;
	private GlibButton removeButton;
	private int selectedIndex = -1;
	private boolean updatingField;
	private boolean dirty;

	GexpressTrainCartEditorScreen(Screen parent, String mapName) {
		super(parent, Text.literal(mapName + " / Train Carts"),
			Text.literal("Select a cart region and edit both corners directly."), 0xFFD98BFF);
		this.mapName = mapName;
		MapPreset map = ClientPresetCache.snapshot().get(mapName);
		this.trainName = GexpressTrainCartsScreen.trainName(map);
		TrainPreset original = trainName == null ? null : ClientTrainPresetCache.snapshot().get(trainName);
		this.edit = original == null ? null : GexpressTrainCartsScreen.mutableTrainPreset(trainName, original);
		if (edit != null && edit.trainCarts != null && !edit.trainCarts.isEmpty()) selectedIndex = 0;
	}

	@Override
	protected void initWorkbench(GlibRect workspace) {
		GlibRect detail = detailRect(workspace);
		coordinates = addDrawableChild(new GlibTextField(textRenderer, detail.x() + 18, detail.y() + 104,
			detail.width() - 36, 28, Text.literal("Six coordinates")));
		coordinates.setMaxLength(160);
		coordinates.setPlaceholder(Text.literal("x1 y1 z1 x2 y2 z2"));
		coordinates.setChangedListener(this::updateCoordinates);
		addDrawableChild(new GlibButton(detail.x() + 18, detail.y() + 148, 150, 24,
			Text.literal("Add at my position"), accentColor(), this::addAtPlayer));
		removeButton = addDrawableChild(new GlibButton(detail.x() + 180, detail.y() + 148, 120, 24,
			Text.literal("Remove cart"), 0xFFFF718D, this::removeSelected));
		loadSelectedField();
		updateButtonState();
	}

	@Override
	protected void renderWorkbench(DrawContext context, GlibRect workspace, int mouseX, int mouseY, float delta) {
		GlibRect library = libraryRect(workspace);
		GlibRect detail = detailRect(workspace);
		drawWorkbenchPanel(context, library.x(), library.y(), library.width(), library.height());
		drawWorkbenchPanel(context, detail.x(), detail.y(), detail.width(), detail.height());
		context.drawTextWithShadow(textRenderer, Text.literal("CART REGIONS"), library.x() + 14, library.y() + 15, MUTED);
		context.drawTextWithShadow(textRenderer, Text.literal(trainName == null ? "No linked train" : trainName),
			library.x() + 14, library.y() + 32, 0xFFFFFFFF);

		GlibRect list = listRect(library);
		int y = list.y() - scroll.scrollOffset();
		List<String> rows = edit == null ? List.of() : GexpressTrainCartsScreen.cartRows(edit);
		context.enableScissor(list.x(), list.y(), list.x() + list.width(), list.y() + list.height());
		for (int index = 0; index < rows.size(); index++) {
			boolean selected = index == selectedIndex;
			boolean hovered = mouseX >= list.x() && mouseX <= list.x() + list.width()
				&& mouseY >= y && mouseY <= y + ROW_HEIGHT - 5;
			context.fill(list.x(), y, list.x() + list.width(), y + ROW_HEIGHT - 5,
				selected ? 0xE02D2635 : hovered ? ROW_HOVER : ROW);
			context.fill(list.x(), y, list.x() + (selected ? 3 : 1), y + ROW_HEIGHT - 5, accentColor());
			context.drawTextWithShadow(textRenderer, Text.literal("Cart " + (index + 1)), list.x() + 12, y + 7, 0xFFFFFFFF);
			context.drawTextWithShadow(textRenderer,
				Text.literal(textRenderer.trimToWidth(rows.get(index), list.width() - 24)), list.x() + 12, y + 22, MUTED);
			y += ROW_HEIGHT;
		}
		if (rows.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal(edit == null ? "Train preset unavailable" : "No cart regions yet"),
				list.x() + list.width() / 2, list.y() + 28, MUTED);
		}
		context.disableScissor();
		scroll.drawScrollbar(context, list, rows.size() * ROW_HEIGHT, accentColor());

		context.drawTextWithShadow(textRenderer,
			Text.literal(selectedIndex < 0 ? "New cart region" : "Cart " + (selectedIndex + 1)),
			detail.x() + 18, detail.y() + 18, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.literal("REGION INSPECTOR"), detail.x() + 18, detail.y() + 35, MUTED);
		context.fill(detail.x() + 18, detail.y() + 52, detail.x() + 72, detail.y() + 54, accentColor());
		context.drawTextWithShadow(textRenderer, Text.literal("Corners (x1 y1 z1 x2 y2 z2)"),
			detail.x() + 18, detail.y() + 86, MUTED);
		context.drawTextWithShadow(textRenderer,
			Text.literal("Values are normalized into a valid region as you type."),
			detail.x() + 18, detail.y() + 190, MUTED);
		updateButtonState();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (helpActive()) return super.mouseClicked(mouseX, mouseY, button);
		if (button == 0 && edit != null) {
			GlibRect list = listRect(libraryRect(workspaceRect()));
			if (list.contains(mouseX, mouseY)) {
				int index = (int) ((mouseY - list.y() + scroll.scrollOffset()) / ROW_HEIGHT);
				if (index >= 0 && index < edit.cartCount()) {
					selectedIndex = index;
					loadSelectedField();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (helpActive()) return true;
		GlibRect list = listRect(libraryRect(workspaceRect()));
		int contentHeight = edit == null ? 0 : edit.cartCount() * ROW_HEIGHT;
		if (scroll.handleMouseScrolled(list, contentHeight, mouseX, mouseY, verticalAmount)) return true;
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private void updateCoordinates(String value) {
		if (updatingField || edit == null || selectedIndex < 0 || selectedIndex >= edit.cartCount()) return;
		TrainPreset.CartData parsed = GexpressTrainCartsScreen.parseCart(value);
		coordinates.setEditableColor(parsed == null ? 0xFFFF718D : 0xFFFFFFFF);
		if (parsed != null) {
			edit.trainCarts.set(selectedIndex, parsed);
			dirty = true;
		}
	}

	private void addAtPlayer() {
		if (edit == null) return;
		TrainPreset.CartData cart = GexpressTrainCartsScreen.parseCart(GexpressTrainCartsScreen.currentPlayerCartString());
		if (cart == null) return;
		edit.trainCarts.add(cart);
		selectedIndex = edit.trainCarts.size() - 1;
		dirty = true;
		loadSelectedField();
	}

	private void removeSelected() {
		if (edit == null || selectedIndex < 0 || selectedIndex >= edit.cartCount()) return;
		edit.trainCarts.remove(selectedIndex);
		selectedIndex = edit.trainCarts.isEmpty() ? -1 : Math.min(selectedIndex, edit.trainCarts.size() - 1);
		dirty = true;
		loadSelectedField();
	}

	private void loadSelectedField() {
		if (coordinates == null) return;
		updatingField = true;
		String value = "";
		if (edit != null && selectedIndex >= 0 && selectedIndex < edit.cartCount()) {
			value = GexpressTrainCartsScreen.cartRows(edit).get(selectedIndex);
		}
		coordinates.setText(value);
		coordinates.setEditableColor(0xFFFFFFFF);
		updatingField = false;
	}

	private void updateButtonState() {
		if (coordinates != null) coordinates.active = edit != null && selectedIndex >= 0;
		if (removeButton != null) removeButton.active = edit != null && selectedIndex >= 0;
	}

	@Override
	public void close() {
		if (dirty) GexpressOptionsScreen.finishGlibSave();
		super.close();
	}

	private static GlibRect libraryRect(GlibRect workspace) {
		return new GlibRect(workspace.x(), workspace.y(), Math.min(360, workspace.width() * 2 / 5), workspace.height());
	}

	private static GlibRect detailRect(GlibRect workspace) {
		GlibRect left = libraryRect(workspace);
		return new GlibRect(left.x() + left.width() + 10, workspace.y(), workspace.width() - left.width() - 10, workspace.height());
	}

	private static GlibRect listRect(GlibRect library) {
		return new GlibRect(library.x() + 10, library.y() + 55, library.width() - 20, library.height() - 65);
	}
}
