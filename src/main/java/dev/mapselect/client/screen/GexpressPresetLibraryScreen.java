package dev.mapselect.client.screen;

import dev.mapselect.client.preset.ClientPresetCache;
import dev.mapselect.client.preset.ClientTrainPresetCache;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.train.TrainPreset;
import io.github.caspe.glib.api.client.editor.GlibWorkbenchScreen;
import io.github.caspe.glib.api.client.ui.GlibButton;
import io.github.caspe.glib.api.client.ui.GlibRect;
import io.github.caspe.glib.api.client.ui.GlibScrollableRegion;
import io.github.caspe.glib.api.client.ui.GlibSearchField;
import io.github.caspe.glib.api.client.ui.GlibUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compact Gambit-style preset browser: searchable library on the left, focused inspector on the right. */
final class GexpressPresetLibraryScreen extends GlibWorkbenchScreen {
	private static final int LINE = 0x80394655;
	private static final int MUTED = 0xFF9EA7B3;
	private static final int ROW = 0xC019222E;
	private static final int ROW_HOVER = 0xE01D2835;
	private static final int ROW_HEIGHT = 48;

	private final Mode mode;
	private Map<String, MapPreset> maps = ClientPresetCache.snapshot();
	private Map<String, TrainPreset> trains = ClientTrainPresetCache.snapshot();
	private final GlibScrollableRegion scroll = new GlibScrollableRegion().scrollStep(40);
	private final List<String> visibleNames = new ArrayList<>();
	private GlibSearchField search;
	private GlibButton openButton;
	private String selectedName;

	private GexpressPresetLibraryScreen(Screen parent, Mode mode) {
		super(parent, Text.literal(mode.title), Text.literal(mode.subtitle), mode.accent);
		this.mode = mode;
		refreshVisible();
	}

	static Screen maps(Screen parent) {
		return new GexpressPresetLibraryScreen(parent, Mode.MAPS);
	}

	static Screen trainCarts(Screen parent) {
		return new GexpressPresetLibraryScreen(parent, Mode.TRAIN_CARTS);
	}

	@Override
	protected void initWorkbench(GlibRect workspace) {
		maps = ClientPresetCache.snapshot();
		trains = ClientTrainPresetCache.snapshot();
		refreshVisible();
		GlibRect library = libraryRect(workspace);
		GlibRect inspector = inspectorRect(workspace);
		search = addDrawableChild(new GlibSearchField(textRenderer, library.x() + 12, library.y() + 12,
			library.width() - 24, 26, Text.literal("Search presets")));
		search.setPlaceholder(Text.literal("Search presets..."));
		search.setChangedListener(value -> {
			refreshVisible();
			scroll.reset();
		});
		openButton = addDrawableChild(new GlibButton(inspector.x() + inspector.width() - 188,
			inspector.y() + inspector.height() - 38, 174, 24,
			Text.literal(mode == Mode.MAPS ? "Open map workspace" : "Edit train carts"),
			accentColor(), this::openSelected));
		updateButtonState();
	}

	@Override
	protected void renderWorkbench(DrawContext context, GlibRect workspace, int mouseX, int mouseY, float delta) {
		GlibRect library = libraryRect(workspace);
		GlibRect inspector = inspectorRect(workspace);
		drawWorkbenchPanel(context, library.x(), library.y(), library.width(), library.height());
		drawWorkbenchPanel(context, inspector.x(), inspector.y(), inspector.width(), inspector.height());

		context.drawTextWithShadow(textRenderer, Text.literal("PRESET LIBRARY"), library.x() + 12, library.y() + 47, MUTED);
		GlibRect list = listRect(library);
		int y = list.y() - scroll.scrollOffset();
		context.enableScissor(list.x(), list.y(), list.x() + list.width(), list.y() + list.height());
		for (String name : visibleNames) {
			boolean selected = name.equals(selectedName);
			boolean hovered = mouseX >= list.x() && mouseX <= list.x() + list.width()
				&& mouseY >= y && mouseY <= y + ROW_HEIGHT - 5;
			int fill = selected ? 0xE0222B38 : hovered ? ROW_HOVER : ROW;
			context.fill(list.x(), y, list.x() + list.width(), y + ROW_HEIGHT - 5, fill);
			context.fill(list.x(), y, list.x() + (selected ? 3 : 1), y + ROW_HEIGHT - 5,
				selected ? accentColor() : LINE);
			String title = textRenderer.trimToWidth(GlibUi.singleLine(name), list.width() - 28);
			context.drawTextWithShadow(textRenderer, Text.literal(title), list.x() + 12, y + 8, 0xFFFFFFFF);
			String summary = mode == Mode.MAPS ? mapSummary(maps.get(name)) : trainSummary(maps.get(name));
			context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(summary, list.width() - 28)),
				list.x() + 12, y + 24, MUTED);
			y += ROW_HEIGHT;
		}
		if (visibleNames.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal("No matching presets"),
				list.x() + list.width() / 2, list.y() + 30, MUTED);
		}
		context.disableScissor();
		scroll.drawScrollbar(context, list, visibleNames.size() * ROW_HEIGHT, accentColor());

		renderInspector(context, inspector);
		updateButtonState();
	}

	private void renderInspector(DrawContext context, GlibRect rect) {
		if (selectedName == null || !maps.containsKey(selectedName)) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal("Select a preset to inspect it"),
				rect.x() + rect.width() / 2, rect.y() + rect.height() / 2, MUTED);
			return;
		}
		MapPreset map = maps.get(selectedName);
		context.drawTextWithShadow(textRenderer, Text.literal(selectedName), rect.x() + 18, rect.y() + 18, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.literal(mode == Mode.MAPS ? "MAP WORKSPACE" : "TRAIN CART WORKSPACE"),
			rect.x() + 18, rect.y() + 35, MUTED);
		context.fill(rect.x() + 18, rect.y() + 52, rect.x() + 72, rect.y() + 54, accentColor());

		if (mode == Mode.MAPS) {
			metric(context, rect, 0, "Terrain", map.terrainMode == null ? "Default" : map.terrainMode.name());
			metric(context, rect, 1, "Play area", map.playAreaMode == null ? "Train" : map.playAreaMode.name());
			metric(context, rect, 2, "Rooms", Integer.toString(MapPreset.normalizeRoomCount(map.roomCount)));
			metric(context, rect, 3, "Spawns", Integer.toString(map.randomSpawnPositions == null ? 0 : map.randomSpawnPositions.size()));
			metric(context, rect, 4, "Train preset", blankFallback(map.defaultTrainPreset, "Not linked"));
			metric(context, rect, 5, "Game settings", blankFallback(map.gameSettingsPreset, "Default"));
		} else {
			String trainName = GexpressTrainCartsScreen.trainName(map);
			TrainPreset train = trainName == null ? null : trains.get(trainName);
			metric(context, rect, 0, "Linked train", blankFallback(trainName, "Not linked"));
			metric(context, rect, 1, "Cart regions", train == null ? "0" : Integer.toString(train.cartCount()));
			metric(context, rect, 2, "Teleport slots", train == null ? "0" : Integer.toString(train.slotCount()));
			metric(context, rect, 3, "Reset template", train != null && train.resetTemplateArea != null ? "Configured" : "Missing");
			context.drawTextWithShadow(textRenderer,
				Text.literal(train == null ? "Link a valid train preset in the map workspace first."
					: "Cart regions can be selected and edited directly in one workspace."),
				rect.x() + 18, rect.y() + 286, MUTED);
		}
	}

	private void metric(DrawContext context, GlibRect rect, int index, String label, String value) {
		int column = index % 2;
		int row = index / 2;
		int gap = 10;
		int width = (rect.width() - 46 - gap) / 2;
		int x = rect.x() + 18 + column * (width + gap);
		int y = rect.y() + 72 + row * 62;
		context.fill(x, y, x + width, y + 52, ROW);
		context.fill(x, y, x + 2, y + 52, GlibUi.withAlpha(accentColor(), 0xB0));
		context.drawTextWithShadow(textRenderer, Text.literal(label), x + 10, y + 9, MUTED);
		context.drawTextWithShadow(textRenderer,
			Text.literal(textRenderer.trimToWidth(GlibUi.singleLine(value), width - 20)), x + 10, y + 28, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (helpActive()) return super.mouseClicked(mouseX, mouseY, button);
		if (button == 0) {
			GlibRect list = listRect(libraryRect(workspaceRect()));
			if (list.contains(mouseX, mouseY)) {
				int index = (int) ((mouseY - list.y() + scroll.scrollOffset()) / ROW_HEIGHT);
				if (index >= 0 && index < visibleNames.size()) {
					selectedName = visibleNames.get(index);
					updateButtonState();
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
		if (scroll.handleMouseScrolled(list, visibleNames.size() * ROW_HEIGHT, mouseX, mouseY, verticalAmount)) return true;
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private void refreshVisible() {
		String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		visibleNames.clear();
		maps.keySet().stream().filter(name -> name.toLowerCase(Locale.ROOT).contains(query)).sorted().forEach(visibleNames::add);
		if (selectedName == null || !visibleNames.contains(selectedName)) {
			selectedName = visibleNames.isEmpty() ? null : visibleNames.getFirst();
		}
		updateButtonState();
	}

	private void updateButtonState() {
		if (openButton == null) return;
		boolean valid = selectedName != null;
		if (valid && mode == Mode.TRAIN_CARTS) {
			String trainName = GexpressTrainCartsScreen.trainName(maps.get(selectedName));
			valid = trainName != null && trains.containsKey(trainName);
		}
		openButton.active = valid;
	}

	private void openSelected() {
		if (selectedName == null) return;
		if (mode == Mode.MAPS) GexpressOptionsScreen.navigateMaps(this, selectedName);
		else GexpressOptionsScreen.navigateTrainCarts(this, selectedName);
	}

	private static GlibRect libraryRect(GlibRect workspace) {
		return new GlibRect(workspace.x(), workspace.y(), Math.min(330, workspace.width() / 3), workspace.height());
	}

	private static GlibRect inspectorRect(GlibRect workspace) {
		GlibRect left = libraryRect(workspace);
		return new GlibRect(left.x() + left.width() + 10, workspace.y(),
			workspace.width() - left.width() - 10, workspace.height());
	}

	private static GlibRect listRect(GlibRect library) {
		return new GlibRect(library.x() + 10, library.y() + 66, library.width() - 20, library.height() - 76);
	}

	private static String mapSummary(MapPreset map) {
		if (map == null) return "Unavailable";
		return MapPreset.normalizeRoomCount(map.roomCount) + " rooms  •  " + blankFallback(map.defaultTrainPreset, "no train");
	}

	private String trainSummary(MapPreset map) {
		String trainName = GexpressTrainCartsScreen.trainName(map);
		TrainPreset train = trainName == null ? null : trains.get(trainName);
		return trainName == null ? "No train linked" : trainName + "  •  " + (train == null ? 0 : train.cartCount()) + " carts";
	}

	private static String blankFallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private enum Mode {
		MAPS("Maps", "Search a preset, inspect it, and open its workspace directly.", 0xFFF4B860),
		TRAIN_CARTS("Train Carts", "Choose a map and manage its linked train cart regions.", 0xFFD98BFF);

		private final String title;
		private final String subtitle;
		private final int accent;

		Mode(String title, String subtitle, int accent) {
			this.title = title;
			this.subtitle = subtitle;
			this.accent = accent;
		}
	}
}
