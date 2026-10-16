package dev.mapselect.client.screen;

import io.github.caspe.glib.api.client.ui.GlibButton;
import io.github.caspe.glib.api.client.ui.GlibLoadingSurface;
import io.github.caspe.glib.api.client.ui.GlibLoadingTransitionScreen;
import io.github.caspe.glib.api.client.ui.GlibRect;
import io.github.caspe.glib.api.client.ui.GlibScrollableRegion;
import io.github.caspe.glib.api.client.ui.GlibSearchField;
import io.github.caspe.glib.api.client.ui.GlibTextField;
import io.github.caspe.glib.api.client.ui.GlibTutorialOverlay;
import io.github.caspe.glib.api.client.ui.GlibUi;
import io.github.caspe.glib.api.client.ui.GlibWorkTree;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * G'Express's concrete settings workspace. G'Lib supplies its reusable controls, search,
 * loading surface, tutorial overlay, and fixed work-tree coordinate system.
 */
@Environment(EnvType.CLIENT)
final class GexpressSettingsEditorScreen extends Screen implements GlibLoadingSurface {
	private static final int PANEL = 0xF0121720;
	private static final int PANEL_SOFT = 0xE0161D28;
	private static final int WORKSPACE = 0xB0101620;
	private static final int CARD = 0xD0192430;
	private static final int CARD_HOVER = 0xED202C39;
	private static final int LINE = 0x80394655;
	private static final int MUTED = 0xFF9EA7B3;
	private static final int WARN = 0xFFFFC857;
	private static final int ROOT_CARD_HEIGHT = 62;
	private static final int CHILD_CARD_HEIGHT = 50;
	private static final int ATTACHED_CARD_GAP = 3;
	private static final int TREE_CARD_WIDTH = 224;
	private static final int TREE_COLUMN_GAP = 22;
	private static final int TREE_GROUP_GAP = 24;
	private static final float GROUP_ANIMATION_SECONDS = 0.24F;

	private final Screen parent;
	private final Text subtitle;
	private final int accentColor;
	private final List<Page> pages;
	private final Runnable saveAction;
	private final Runnable cancelAction;
	private final boolean liveChanges;
	private final GlibScrollableRegion paletteScroll = new GlibScrollableRegion().scrollStep(30);
	private final GlibWorkTree workspaceTree = new GlibWorkTree().zoomRange(0.55D, 1.35D);
	private final Set<String> expandedGroups = new HashSet<>();
	private final Map<String, GroupAnimation> groupAnimations = new HashMap<>();
	private final GlibTutorialOverlay help;

	private int selectedPage;
	private int selectedSection;
	private int selectedEntry;
	private ClickableWidget inspectorControl;
	private GlibButton backButton;
	private GlibButton helpButton;
	private GlibButton doneButton;
	private GlibSearchField searchField;
	private String searchQuery = "";
	private boolean completed;

	private GexpressSettingsEditorScreen(Builder builder) {
		super(builder.title);
		this.parent = builder.parent;
		this.subtitle = builder.subtitle;
		this.accentColor = builder.accentColor;
		this.pages = builder.pages.isEmpty()
			? List.of(new Page(Text.literal("Settings"), Text.empty(), List.of()))
			: List.copyOf(builder.pages);
		this.saveAction = builder.saveAction;
		this.cancelAction = builder.cancelAction;
		this.liveChanges = builder.liveChanges;
		this.help = new GlibTutorialOverlay(helpSteps(), accentColor, () -> {})
			.onStepChanged((index, step) -> prepareHelpStep(step.targetKey()));
		resetWorkspaceView();
	}

	public static Builder builder(Screen parent, Text title) {
		return new Builder(parent, title);
	}

	@Override
	protected void init() {
		rebuildControls();
	}

	private void rebuildControls() {
		clearChildren();
		clampSelection();
		GlibRect panel = panelRect();
		int right = panel.x() + panel.width() - 18;
		backButton = addDrawableChild(new GlibButton(right - 228, panel.y() + 12, 68, 22,
			Text.literal("Back"), WARN, this::back));
		helpButton = addDrawableChild(new GlibButton(right - 152, panel.y() + 12, 68, 22,
			Text.literal("Help"), accentColor, help::start));
		doneButton = addDrawableChild(new GlibButton(right - 76, panel.y() + 12, 68, 22,
			Text.literal("Done"), 0xFF6D7787, this::saveAndClose));

		GlibRect search = searchRect();
		searchField = addDrawableChild(new GlibSearchField(textRenderer, search.x(), search.y(), search.width(), search.height(),
			Text.literal("Search settings")).placeholder(Text.literal("Search settings...")));
		searchField.setMaxLength(128);
		searchField.setText(searchQuery);
		searchField.setChangedListener(value -> {
			searchQuery = value;
			selectedEntry = -1;
			resetWorkspaceView();
			if (inspectorControl != null) {
				inspectorControl.visible = false;
				inspectorControl.active = false;
			}
		});

		Entry entry = selectedEntry();
		if (entry != null) {
			GlibRect control = inspectorControlRect();
			inspectorControl = createWidget(entry, control.x(), control.y(), control.width(), control.height());
			if (inspectorControl != null) addDrawableChild(inspectorControl);
		} else {
			inspectorControl = null;
		}
	}

	private ClickableWidget createWidget(Entry entry, int x, int y, int width, int height) {
		if (entry instanceof ToggleEntry toggle) {
			return new GlibButton(x, y, width, height, toggle.formatter().apply(toggle.value().get()), accentColor,
				() -> toggle.setter().accept(!toggle.value().get()));
		}
		if (entry instanceof CycleEntry cycle) {
			return new GlibButton(x, y, width, height, cycle.valueText().get(), accentColor, cycle.cycle());
		}
		if (entry instanceof SliderEntry slider) {
			GlibTextField field = new GlibTextField(textRenderer, x, y, width, height, slider.label());
			field.setMaxLength(48);
			field.setText(formatNumber(slider.value().getAsDouble()));
			field.setChangedListener(value -> {
				Double parsed = parseNumber(value);
				boolean valid = parsed != null && parsed >= slider.min() && parsed <= slider.max();
				field.setEditableColor(valid ? 0xFFFFFFFF : 0xFFFF718D);
				if (valid) slider.setter().accept(parsed);
			});
			return field;
		}
		if (entry instanceof TextEntry text) {
			GlibTextField field = new GlibTextField(textRenderer, x, y, width, height, text.label());
			field.setMaxLength(text.maxLength());
			field.setText(text.value().get());
			field.setChangedListener(value -> {
				boolean valid = text.validator().test(value);
				field.setEditableColor(valid ? 0xFFFFFFFF : 0xFFFF718D);
				if (valid) text.setter().accept(value);
			});
			return field;
		}
		if (entry instanceof ActionEntry action) {
			return new GlibButton(x, y, width, height, action.buttonText(), accentColor, action.action());
		}
		return null;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderShell(context);
		renderPalette(context, mouseX, mouseY);
		renderWorkspace(context, mouseX, mouseY);
		renderInspector(context);
		refreshControl();
		super.render(context, mouseX, mouseY, delta);
		paletteScroll.drawScrollbar(context, sectionsViewport(), sectionContentHeight(), accentColor);
		if (help.active()) {
			context.getMatrices().push();
			context.getMatrices().translate(0.0F, 0.0F, 1000.0F);
			help.startFadeIfNeeded();
			help.render(context, textRenderer, width, height, mouseX, mouseY, this::helpTarget);
			context.getMatrices().pop();
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	private void renderShell(DrawContext context) {
		GlibRect panel = panelRect();
		context.fill(0, 0, width, height, 0x62060A10);
		GlibUi.drawPanel(context, panel.x(), panel.y(), panel.width(), panel.height(), PANEL, LINE);
		context.drawTextWithShadow(textRenderer, GlibUi.singleLine(title), panel.x() + 18, panel.y() + 18, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, GlibUi.singleLine(subtitle), panel.x() + 18, panel.y() + 35, MUTED);
		GlibUi.drawPanel(context, paletteRect().x(), paletteRect().y(), paletteRect().width(), paletteRect().height(), PANEL_SOFT, LINE);
		GlibUi.drawPanel(context, workspaceRect().x(), workspaceRect().y(), workspaceRect().width(), workspaceRect().height(), WORKSPACE, LINE);
		GlibUi.drawPanel(context, inspectorRect().x(), inspectorRect().y(), inspectorRect().width(), inspectorRect().height(), PANEL_SOFT, LINE);
		String status = liveChanges ? "Autosave on" : "Save changes with Done";
		context.fill(panel.x() + 18, panel.y() + panel.height() - 24, panel.x() + 22, panel.y() + panel.height() - 20,
			liveChanges ? accentColor : WARN);
		context.drawTextWithShadow(textRenderer, Text.literal(status), panel.x() + 28,
			panel.y() + panel.height() - 27, liveChanges ? accentColor : MUTED);
	}

	private void renderPalette(DrawContext context, int mouseX, int mouseY) {
		GlibRect palette = paletteRect();
		context.drawTextWithShadow(textRenderer, Text.literal("Settings Palette"), palette.x() + 12, palette.y() + 12, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.literal("Workspaces"), palette.x() + 12, palette.y() + 68, MUTED);
		for (int index = 0; index < pages.size(); index++) {
			GlibRect row = pageRowRect(index);
			boolean selected = index == selectedPage;
			boolean hovered = row.contains(mouseX, mouseY);
			context.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(),
				selected ? 0xC0263342 : hovered ? 0xA0222B38 : 0x7019202B);
			if (selected) context.fill(row.x(), row.y(), row.x() + 3, row.y() + row.height(), accentColor);
			context.drawTextWithShadow(textRenderer,
				Text.literal(textRenderer.trimToWidth(GlibUi.singleLine(pages.get(index).title().getString()), row.width() - 18)),
				row.x() + 9, row.y() + 9, selected ? 0xFFFFFFFF : 0xFFD0D8E3);
		}

		int sectionLabelY = sectionsStartY() - 19;
		context.drawTextWithShadow(textRenderer, Text.literal("Sections"), palette.x() + 12, sectionLabelY, MUTED);
		List<Section> sections = currentPage().sections();
		GlibRect viewport = sectionsViewport();
		context.enableScissor(viewport.x(), viewport.y(), viewport.x() + viewport.width(), viewport.y() + viewport.height());
		for (int index = 0; index < sections.size(); index++) {
			GlibRect row = sectionRowRect(index);
			if (row.y() + row.height() < viewport.y() || row.y() > viewport.y() + viewport.height()) continue;
			boolean selected = index == selectedSection;
			boolean hovered = row.contains(mouseX, mouseY);
			context.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(),
				selected ? 0xA0222B38 : hovered ? 0x801F2935 : 0x5019202B);
			context.drawTextWithShadow(textRenderer,
				Text.literal(textRenderer.trimToWidth(GlibUi.singleLine(sections.get(index).title().getString()), row.width() - 24)),
				row.x() + 12, row.y() + 8, selected ? accentColor : 0xFFD0D8E3);
			if (selected) context.drawTextWithShadow(textRenderer, Text.literal(">"), row.x() + row.width() - 14, row.y() + 8, accentColor);
		}
		context.disableScissor();
	}

	private void renderWorkspace(DrawContext context, int mouseX, int mouseY) {
		GlibRect area = workspaceRect();
		Section section = currentSection();
		List<WorkspaceEntry> entries = workspaceEntries();
		boolean searching = searchActive();
		context.drawTextWithShadow(textRenderer, Text.literal("Settings Work Tree"), area.x() + 12, area.y() + 12, 0xFFFFFFFF);
		String count = entries.size() + (entries.size() == 1 ? " setting" : " settings")
			+ "  |  " + Math.round(workspaceTree.zoom() * 100.0D) + "%";
		context.drawTextWithShadow(textRenderer, Text.literal(count), area.x() + area.width() - textRenderer.getWidth(count) - 12,
			area.y() + 12, MUTED);
		if (searching) {
			context.drawTextWithShadow(textRenderer, Text.literal("Search results"), area.x() + 12, area.y() + 32, 0xFFE5EBF3);
			String description = textRenderer.trimToWidth("Across every workspace and section for \"" + searchQuery.strip() + "\"", area.width() - 24);
			context.drawTextWithShadow(textRenderer, Text.literal(description), area.x() + 12, area.y() + 47, MUTED);
		} else if (section != null) {
			context.drawTextWithShadow(textRenderer, GlibUi.singleLine(section.title()), area.x() + 12, area.y() + 32, 0xFFE5EBF3);
			String description = textRenderer.trimToWidth(GlibUi.singleLine(section.description().getString()), area.width() - 24);
			if (!description.isBlank()) context.drawTextWithShadow(textRenderer, Text.literal(description), area.x() + 12, area.y() + 47, MUTED);
		}
		drawWorkspaceGrid(context);

		GlibRect cards = workspaceCardsRect();
		context.enableScissor(cards.x(), cards.y(), cards.x() + cards.width(), cards.y() + cards.height());
		if (entries.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal(searching ? "No matching settings" : "No settings in this section"),
				cards.x() + cards.width() / 2, cards.y() + cards.height() / 2, MUTED);
		} else {
			GlibWorkTree.Point worldMouse = workspaceTree.screenToWorld(cards, mouseX, mouseY);
			context.getMatrices().push();
			context.getMatrices().translate(cards.x() + workspaceTree.panX(), cards.y() + workspaceTree.panY(), 0.0D);
			context.getMatrices().scale((float) workspaceTree.zoom(), (float) workspaceTree.zoom(), 1.0F);
			for (PlacedWorkspaceEntry placed : placedWorkspaceEntries(entries)) {
				WorkspaceEntry workspaceEntry = placed.workspaceEntry();
				Entry entry = workspaceEntry.entry();
				GlibRect card = placed.worldRect();
				GlibRect visibleCard = placed.visibleWorldRect();
				GlibRect screenVisibleCard = workspaceTree.worldRectToScreen(cards, visibleCard);
				if (!GlibScrollableRegion.intersects(screenVisibleCard, cards)) continue;
				if (!placed.root()) {
					context.enableScissor(screenVisibleCard.x(), screenVisibleCard.y(),
						screenVisibleCard.x() + screenVisibleCard.width(), screenVisibleCard.y() + screenVisibleCard.height());
				}
				boolean selected = placed.entryIndex() == selectedEntry;
				boolean hovered = visibleCard.contains(worldMouse.x(), worldMouse.y()) && cards.contains(mouseX, mouseY);
				boolean expandable = placed.root() && placed.groupSize() > 1 && !searching;
				boolean toggleHovered = expandable && groupToggleRect(placed).contains(worldMouse.x(), worldMouse.y());
				int fill = placed.root() ? 0xE01C2835 : CARD;
				GlibUi.drawPanel(context, card.x(), card.y(), card.width(), card.height(),
					selected || hovered ? CARD_HOVER : fill, selected ? accentColor : LINE);
				context.fill(card.x(), card.y(), card.x() + 3, card.y() + card.height(),
					entry.available().getAsBoolean() ? accentColor : 0xFF647282);
				String label = textRenderer.trimToWidth(GlibUi.singleLine(entry.label().getString()),
					card.width() - (expandable ? 62 : 48));
				String summary = searching
					? workspaceEntry.context() + (entrySummary(entry).isBlank() ? "" : "  |  " + entrySummary(entry))
					: entrySummary(entry);
				if (expandable && !searching) {
					summary += (summary.isBlank() ? "" : "  |  ") + (placed.groupSize() - 1) + " attached";
				}
				summary = textRenderer.trimToWidth(summary, card.width() - (expandable ? 62 : 48));
				int labelY = card.y() + (placed.root() ? 13 : 9);
				int summaryY = card.y() + (placed.root() ? 33 : 27);
				context.drawTextWithShadow(textRenderer, Text.literal(label), card.x() + 14, labelY,
					entry.available().getAsBoolean() ? 0xFFFFFFFF : MUTED);
				if (!summary.isBlank()) context.drawTextWithShadow(textRenderer, Text.literal(summary), card.x() + 14, summaryY,
					MUTED);
				if (expandable) {
					GlibRect toggle = groupToggleRect(placed);
					if (toggleHovered) context.fill(toggle.x(), toggle.y(), toggle.x() + toggle.width(), toggle.y() + toggle.height(), 0x502E3B49);
					context.drawCenteredTextWithShadow(textRenderer, Text.literal(placed.expanded() ? "-" : "+"),
						toggle.x() + toggle.width() / 2, toggle.y() + (toggle.height() - 8) / 2,
						toggleHovered ? 0xFFFFFFFF : accentColor);
				} else {
					context.drawTextWithShadow(textRenderer, Text.literal(">"), card.x() + card.width() - 20,
						card.y() + (card.height() - 8) / 2, selected ? accentColor : 0xFF687586);
				}
				if (!placed.root()) context.disableScissor();
			}
			context.getMatrices().pop();
		}
		context.disableScissor();
	}

	private void drawWorkspaceGrid(DrawContext context) {
		GlibRect cards = workspaceCardsRect();
		double spacing = Math.max(20.0D, 48.0D * workspaceTree.zoom());
		double offsetX = ((workspaceTree.panX() % spacing) + spacing) % spacing;
		double offsetY = ((workspaceTree.panY() % spacing) + spacing) % spacing;
		for (double x = cards.x() + offsetX; x < cards.x() + cards.width(); x += spacing) {
			int pixel = (int) Math.round(x);
			context.fill(pixel, cards.y(), pixel + 1, cards.y() + cards.height(), 0x201E2A37);
		}
		for (double y = cards.y() + offsetY; y < cards.y() + cards.height(); y += spacing) {
			int pixel = (int) Math.round(y);
			context.fill(cards.x(), pixel, cards.x() + cards.width(), pixel + 1, 0x201E2A37);
		}
	}

	private void renderInspector(DrawContext context) {
		GlibRect inspector = inspectorRect();
		context.drawTextWithShadow(textRenderer, Text.literal("Inspector"), inspector.x() + 12, inspector.y() + 12, 0xFFFFFFFF);
		Entry entry = selectedEntry();
		if (entry == null) {
			context.drawTextWithShadow(textRenderer, Text.literal("Select a setting card to edit it."),
				inspector.x() + 12, inspector.y() + 38, MUTED);
			return;
		}
		String label = textRenderer.trimToWidth(GlibUi.singleLine(entry.label().getString()), inspector.width() - 24);
		context.drawTextWithShadow(textRenderer, Text.literal(label), inspector.x() + 12, inspector.y() + 42,
			entry.available().getAsBoolean() ? 0xFFFFFFFF : MUTED);
		context.fill(inspector.x() + 12, inspector.y() + 61, inspector.x() + 70, inspector.y() + 63, accentColor);
		List<OrderedText> lines = textRenderer.wrapLines(entry.description(), inspector.width() - 24);
		for (int index = 0; index < Math.min(6, lines.size()); index++) {
			context.drawTextWithShadow(textRenderer, lines.get(index), inspector.x() + 12,
				inspector.y() + 76 + index * 13, MUTED);
		}
		GlibRect control = inspectorControlRect();
		context.drawTextWithShadow(textRenderer,
			Text.literal(entry instanceof ActionEntry ? "Action" : entry instanceof LabelEntry ? "Information" : "Value"),
			control.x(), control.y() - 17, MUTED);
		if (entry instanceof LabelEntry) {
			context.fill(control.x(), control.y(), control.x() + control.width(), control.y() + 1, LINE);
			context.drawTextWithShadow(textRenderer, Text.literal("This entry is informational."), control.x(), control.y() + 13, MUTED);
		}
	}

	private void refreshControl() {
		Entry entry = selectedEntry();
		if (inspectorControl == null || entry == null) return;
		inspectorControl.active = entry.available().getAsBoolean();
		if (entry instanceof ToggleEntry toggle) {
			inspectorControl.setMessage(toggle.formatter().apply(toggle.value().get()));
		} else if (entry instanceof CycleEntry cycle) {
			inspectorControl.setMessage(cycle.valueText().get());
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (help.active() && help.mouseClicked(mouseX, mouseY, button)) return true;
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (int index = 0; index < pages.size(); index++) {
				if (pageRowRect(index).contains(mouseX, mouseY)) {
					selectPage(index);
					return true;
				}
			}
			if (sectionsViewport().contains(mouseX, mouseY)) {
				for (int index = 0; index < currentPage().sections().size(); index++) {
					if (sectionRowRect(index).contains(mouseX, mouseY)) {
						selectSection(index);
						return true;
					}
				}
			}
			GlibRect cards = workspaceCardsRect();
			List<WorkspaceEntry> entries = workspaceEntries();
			if (!entries.isEmpty() && cards.contains(mouseX, mouseY)) {
				GlibWorkTree.Point worldMouse = workspaceTree.screenToWorld(cards, mouseX, mouseY);
				List<PlacedWorkspaceEntry> placedEntries = placedWorkspaceEntries(entries);
				for (PlacedWorkspaceEntry placed : placedEntries) {
					if (placed.visibleWorldRect().contains(worldMouse.x(), worldMouse.y())) {
						if (!searchActive() && placed.root() && placed.groupSize() > 1
								&& groupToggleRect(placed).contains(worldMouse.x(), worldMouse.y())) {
							boolean collapsing = !expandedGroups.add(placed.groupKey());
							if (collapsing) {
								expandedGroups.remove(placed.groupKey());
								for (PlacedWorkspaceEntry candidate : placedEntries) {
									if (candidate.entryIndex() == selectedEntry
											&& candidate.groupKey().equals(placed.groupKey()) && !candidate.root()) {
										selectEntry(placed.entryIndex());
										return true;
									}
								}
							}
							return true;
						}
						selectEntry(placed.entryIndex());
						return true;
					}
				}
			}
			if (cards.contains(mouseX, mouseY)) {
				workspaceTree.beginPan(mouseX, mouseY);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && workspaceTree.panning()) {
			return workspaceTree.updatePan(mouseX, mouseY);
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && workspaceTree.panning()) {
			workspaceTree.endPan();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (paletteScroll.handleMouseScrolled(sectionsViewport(), sectionContentHeight(),
			mouseX, mouseY, verticalAmount)) return true;
		if (workspaceCardsRect().contains(mouseX, mouseY)) {
			workspaceTree.zoomAt(workspaceCardsRect(), mouseX, mouseY, verticalAmount);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (help.active()) return help.keyPressed(keyCode);
		if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && searchField != null) {
			searchField.setFocused(true);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (searchField != null && (searchField.isFocused() || searchActive())) {
				searchField.setText("");
				searchField.setFocused(false);
				return true;
			}
			back();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void selectPage(int index) {
		if (index < 0 || index >= pages.size() || index == selectedPage && !searchActive()) return;
		searchQuery = "";
		selectedPage = index;
		selectedSection = 0;
		selectedEntry = 0;
		paletteScroll.reset();
		resetWorkspaceView();
		rebuildControls();
	}

	private void selectSection(int index) {
		if (index < 0 || index >= currentPage().sections().size() || index == selectedSection && !searchActive()) return;
		searchQuery = "";
		selectedSection = index;
		selectedEntry = 0;
		resetWorkspaceView();
		rebuildControls();
	}

	private void selectEntry(int index) {
		List<WorkspaceEntry> entries = workspaceEntries();
		if (index < 0 || index >= entries.size() || (index == selectedEntry && inspectorControl != null)) return;
		WorkspaceEntry selected = entries.get(index);
		selectedPage = selected.pageIndex();
		selectedSection = selected.sectionIndex();
		selectedEntry = index;
		rebuildControls();
	}

	private void clampSelection() {
		selectedPage = Math.max(0, Math.min(pages.size() - 1, selectedPage));
		List<Section> sections = currentPage().sections();
		selectedSection = sections.isEmpty() ? 0 : Math.max(0, Math.min(sections.size() - 1, selectedSection));
		List<WorkspaceEntry> entries = workspaceEntries();
		selectedEntry = entries.isEmpty() ? -1
			: selectedEntry < 0 ? -1 : Math.min(entries.size() - 1, selectedEntry);
	}

	private Page currentPage() { return pages.get(selectedPage); }
	private Section currentSection() {
		return currentPage().sections().isEmpty() ? null : currentPage().sections().get(selectedSection);
	}
	private Entry selectedEntry() {
		List<WorkspaceEntry> entries = workspaceEntries();
		return selectedEntry < 0 || selectedEntry >= entries.size() ? null : entries.get(selectedEntry).entry();
	}

	private String entrySummary(Entry entry) {
		if (entry instanceof ToggleEntry toggle) return GlibUi.singleLine(toggle.formatter().apply(toggle.value().get()).getString());
		if (entry instanceof CycleEntry cycle) return GlibUi.singleLine(cycle.valueText().get().getString());
		if (entry instanceof SliderEntry slider) return GlibUi.singleLine(slider.valueText().apply(slider.value().getAsDouble()).getString());
		if (entry instanceof TextEntry text) return GlibUi.singleLine(text.value().get());
		if (entry instanceof ActionEntry action) return GlibUi.singleLine(action.description().getString());
		return GlibUi.singleLine(entry.description().getString());
	}

	private List<WorkspaceEntry> workspaceEntries() {
		List<WorkspaceEntry> result = new ArrayList<>();
		if (searchActive()) {
			String query = normalizeSearch(searchQuery);
			for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
				Page page = pages.get(pageIndex);
				for (int sectionIndex = 0; sectionIndex < page.sections().size(); sectionIndex++) {
					Section section = page.sections().get(sectionIndex);
					String context = GlibUi.singleLine(page.title().getString()) + " / "
						+ GlibUi.singleLine(section.title().getString());
					boolean wholeSection = query.equals(normalizeSearch(page.title().getString()))
						|| query.equals(normalizeSearch(section.title().getString()));
					for (EntryGroup group : entryGroups(section.entries())) {
						List<Entry> matches = new ArrayList<>();
						Entry header = group.entries().get(0);
						boolean wholeGroup = wholeSection || group.explicit()
							&& matchesSearch(query, header, false);
						if (wholeGroup) {
							matches.addAll(group.entries());
						} else {
							for (Entry entry : group.entries()) {
								if (matchesSearch(query, entry, true)) matches.add(entry);
							}
							if (group.explicit() && !matches.isEmpty() && !matches.contains(header)) {
								matches.add(0, header);
							}
						}
						for (int matchIndex = 0; matchIndex < matches.size(); matchIndex++) {
							result.add(new WorkspaceEntry(pageIndex, sectionIndex, matches.get(matchIndex), context,
								!result.isEmpty() && (matchIndex == 0 || !group.explicit())));
						}
					}
				}
			}
			return result;
		}
		Section section = currentSection();
		if (section == null) return result;
		for (EntryGroup group : entryGroups(section.entries())) {
			for (int index = 0; index < group.entries().size(); index++) {
				result.add(new WorkspaceEntry(selectedPage, selectedSection, group.entries().get(index), "",
					!result.isEmpty() && (index == 0 || !group.explicit())));
			}
		}
		return result;
	}

	private List<EntryGroup> entryGroups(List<Entry> entries) {
		List<EntryGroup> groups = new ArrayList<>();
		List<Entry> current = new ArrayList<>();
		for (Entry entry : entries) {
			if (meaningfulEntry(entry)) {
				current.add(entry);
				continue;
			}
			if (!current.isEmpty()) {
				groups.add(new EntryGroup(List.copyOf(current), true));
				current.clear();
			}
		}
		if (!current.isEmpty()) groups.add(new EntryGroup(List.copyOf(current), false));
		return groups;
	}

	private boolean meaningfulEntry(Entry entry) {
		return !GlibUi.singleLine(entry.label().getString()).isBlank()
			|| !GlibUi.singleLine(entry.description().getString()).isBlank()
			|| !entrySummary(entry).isBlank();
	}

	private boolean matchesSearch(String query, Entry entry, boolean includeValue) {
		String haystack = normalizeSearch(entry.label().getString() + " " + entry.description().getString()
			+ (includeValue ? " " + entrySummary(entry) : ""));
		for (String word : query.split("\\s+")) {
			if (!word.isBlank() && !haystack.contains(word)) return false;
		}
		return true;
	}

	private boolean searchActive() { return !searchQuery.isBlank(); }

	private static String normalizeSearch(String value) {
		return GlibUi.singleLine(value == null ? "" : value).strip().toLowerCase(Locale.ROOT);
	}

	private static Double parseNumber(String value) {
		try {
			double parsed = Double.parseDouble(value.strip().replace(',', '.'));
			return Double.isFinite(parsed) ? parsed : null;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String formatNumber(double value) {
		if (!Double.isFinite(value)) return "";
		if (value == Math.rint(value) && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
			return Long.toString((long) value);
		}
		return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	private List<PlacedWorkspaceEntry> placedWorkspaceEntries(List<WorkspaceEntry> entries) {
		List<List<Integer>> groups = new ArrayList<>();
		List<Integer> current = new ArrayList<>();
		for (int index = 0; index < entries.size(); index++) {
			if (index > 0 && entries.get(index).groupStart() && !current.isEmpty()) {
				groups.add(List.copyOf(current));
				current.clear();
			}
			current.add(index);
		}
		if (!current.isEmpty()) groups.add(List.copyOf(current));

		int cardWidth = TREE_CARD_WIDTH;
		List<PlacedWorkspaceEntry> placed = new ArrayList<>();
		for (int columnStart = 0; columnStart < groups.size(); columnStart += 5) {
			int column = columnStart / 5;
			int x = 14 + column * (cardWidth + TREE_COLUMN_GAP);
			int y = 16;
			for (int row = 0; row < 5 && columnStart + row < groups.size(); row++) {
				List<Integer> group = groups.get(columnStart + row);
				String groupKey = groupKey(entries, group);
				boolean expandedTarget = searchActive() || expandedGroups.contains(groupKey);
				float expansion = GlibUi.easeInOutCubic(groupAnimationProgress(groupKey, expandedTarget));
				int groupTop = y;
				int childrenHeight = Math.max(0, group.size() - 1) * (CHILD_CARD_HEIGHT + ATTACHED_CARD_GAP);
				int visibleChildrenHeight = Math.round(childrenHeight * expansion);
				GlibRect childrenViewport = new GlibRect(x, groupTop + ROOT_CARD_HEIGHT + ATTACHED_CARD_GAP,
					cardWidth, visibleChildrenHeight);
				int visibleMembers = visibleChildrenHeight > 0 ? group.size() : 1;
				for (int member = 0; member < visibleMembers; member++) {
					int entryIndex = group.get(member);
					boolean root = member == 0;
					int height = root ? ROOT_CARD_HEIGHT : CHILD_CARD_HEIGHT;
					int inset = root ? 0 : 10;
					int memberY = root ? groupTop : groupTop + ROOT_CARD_HEIGHT + ATTACHED_CARD_GAP
						+ (member - 1) * (CHILD_CARD_HEIGHT + ATTACHED_CARD_GAP);
					GlibRect rect = new GlibRect(x + inset, memberY, cardWidth - inset, height);
					GlibRect visibleRect = root ? rect : intersect(rect, childrenViewport);
					if (visibleRect.isEmpty()) continue;
					placed.add(new PlacedWorkspaceEntry(entryIndex, entries.get(entryIndex), rect, root,
						visibleRect, groupKey, group.size(), expandedTarget));
				}
				y += ROOT_CARD_HEIGHT + ATTACHED_CARD_GAP + visibleChildrenHeight + TREE_GROUP_GAP;
			}
		}
		return placed;
	}

	private String groupKey(List<WorkspaceEntry> entries, List<Integer> group) {
		WorkspaceEntry root = entries.get(group.get(0));
		return root.pageIndex() + ":" + root.sectionIndex() + ":"
			+ Integer.toHexString(System.identityHashCode(root.entry()));
	}

	private float groupAnimationProgress(String key, boolean expanded) {
		long now = System.nanoTime();
		GroupAnimation animation = groupAnimations.get(key);
		if (animation == null) {
			if (!expanded) return 0.0F;
			animation = new GroupAnimation(0.0F, true, now);
			groupAnimations.put(key, animation);
		} else if (animation.expanded != expanded) {
			animation = new GroupAnimation(animation.value(now), expanded, now);
			groupAnimations.put(key, animation);
		}
		return animation.value(now);
	}

	private static GlibRect intersect(GlibRect first, GlibRect second) {
		int left = Math.max(first.x(), second.x());
		int top = Math.max(first.y(), second.y());
		int right = Math.min(first.x() + first.width(), second.x() + second.width());
		int bottom = Math.min(first.y() + first.height(), second.y() + second.height());
		return new GlibRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
	}

	private GlibRect groupToggleRect(PlacedWorkspaceEntry placed) {
		GlibRect card = placed.worldRect();
		return new GlibRect(card.x() + card.width() - 34, card.y() + 5, 29, card.height() - 10);
	}

	private void resetWorkspaceView() {
		workspaceTree.setZoom(0.72D);
		workspaceTree.setPan(10.0D, 10.0D);
		workspaceTree.endPan();
	}

	private void prepareHelpStep(String target) {
		if (!target.startsWith("page:")) return;
		try {
			int page = Integer.parseInt(target.substring("page:".length()));
			if (page >= 0 && page < pages.size() && page != selectedPage) selectPage(page);
		} catch (NumberFormatException ignored) {
		}
	}

	private List<GlibTutorialOverlay.Step> helpSteps() {
		List<GlibTutorialOverlay.Step> steps = new ArrayList<>();
		steps.add(new GlibTutorialOverlay.Step("Settings editor",
			"This workspace keeps navigation, available settings, and the selected value separate so you only edit one thing at a time.", "header"));
		for (int index = 0; index < pages.size(); index++) {
			Page page = pages.get(index);
			String body = GlibUi.singleLine(page.subtitle().getString());
			if (body.isBlank()) body = "Open this workspace to see its grouped settings and tools.";
			steps.add(new GlibTutorialOverlay.Step(GlibUi.singleLine(page.title().getString()), body, "page:" + index));
		}
		steps.add(new GlibTutorialOverlay.Step("Settings work tree",
			"Predetermined groups start minimized in a column-and-row layout. Use + and - on a group to show or hide its attached settings, drag empty space to pan, and use the mouse wheel to zoom. Cards cannot be moved or attached.", "workspace"));
		steps.add(new GlibTutorialOverlay.Step("Focused inspector",
			"The selected setting's explanation and control live here. Changes apply immediately when autosave is enabled.", "inspector"));
		steps.add(new GlibTutorialOverlay.Step("Navigation",
			"Back returns to the previous menu, Help replays this guide, and Done saves and closes the workspace.", "navigation"));
		return steps;
	}

	private GlibRect helpTarget(String key) {
		if ("header".equals(key)) return new GlibRect(panelRect().x() + 10, panelRect().y() + 8, panelRect().width() - 20, 48);
		if ("workspace".equals(key)) return workspaceRect();
		if ("inspector".equals(key)) return inspectorRect();
		if ("navigation".equals(key)) return new GlibRect(backButton.getX(), backButton.getY(),
			doneButton.getX() + doneButton.getWidth() - backButton.getX(), doneButton.getHeight());
		if (key.startsWith("page:")) {
			try { return pageRowRect(Integer.parseInt(key.substring("page:".length()))); }
			catch (NumberFormatException ignored) { return paletteRect(); }
		}
		return paletteRect();
	}

	private void back() {
		if (liveChanges) saveAndClose();
		else cancelAndClose();
	}

	@Override
	public void close() { back(); }

	private void saveAndClose() {
		if (completed) return;
		completed = true;
		saveAction.run();
		returnToParent();
	}

	private void cancelAndClose() {
		if (completed) return;
		completed = true;
		cancelAction.run();
		returnToParent();
	}

	private void returnToParent() {
		if (client == null) return;
		if (parent == null) {
			client.setScreen(null);
			return;
		}
		GlibLoadingTransitionScreen.returnTo(this, "Returning...", accentColor, () -> parent);
	}

	@Override
	public boolean shouldPause() { return false; }

	private GlibRect panelRect() {
		int marginX = width >= 720 ? 24 : 12;
		int marginY = height >= 480 ? 14 : 8;
		return new GlibRect(marginX, marginY, Math.max(1, width - marginX * 2), Math.max(1, height - marginY * 2));
	}

	@Override
	public GlibRect glibLoadingSurface() { return panelRect(); }

	private GlibRect paletteRect() {
		GlibRect panel = panelRect();
		int available = panel.width() - 64;
		int width = Math.max(170, Math.min(250, available / 4));
		return new GlibRect(panel.x() + 18, panel.y() + 72, width, panel.height() - 116);
	}

	private GlibRect inspectorRect() {
		GlibRect panel = panelRect();
		int available = panel.width() - 64;
		int width = Math.max(230, Math.min(360, available * 31 / 100));
		return new GlibRect(panel.x() + panel.width() - width - 18, panel.y() + 72, width, panel.height() - 116);
	}

	private GlibRect workspaceRect() {
		GlibRect palette = paletteRect();
		GlibRect inspector = inspectorRect();
		int x = palette.x() + palette.width() + 14;
		return new GlibRect(x, palette.y(), Math.max(1, inspector.x() - x - 14), palette.height());
	}

	private GlibRect workspaceCardsRect() {
		GlibRect area = workspaceRect();
		return new GlibRect(area.x() + 6, area.y() + 67, Math.max(1, area.width() - 12), Math.max(1, area.height() - 75));
	}

	private GlibRect inspectorControlRect() {
		GlibRect inspector = inspectorRect();
		return new GlibRect(inspector.x() + 12, Math.min(inspector.y() + 190, inspector.y() + inspector.height() - 50),
			Math.max(1, inspector.width() - 24), 24);
	}

	private GlibRect pageRowRect(int index) {
		GlibRect palette = paletteRect();
		return new GlibRect(palette.x() + 10, palette.y() + 82 + index * 31, palette.width() - 20, 26);
	}

	private int sectionsStartY() {
		return paletteRect().y() + 82 + pages.size() * 31 + 28;
	}

	private GlibRect searchRect() {
		GlibRect palette = paletteRect();
		return new GlibRect(palette.x() + 10, palette.y() + 34, palette.width() - 20, 24);
	}

	private GlibRect sectionsViewport() {
		GlibRect palette = paletteRect();
		int y = sectionsStartY();
		return new GlibRect(palette.x() + 6, y, palette.width() - 12,
			Math.max(1, palette.y() + palette.height() - 10 - y));
	}

	private int sectionContentHeight() {
		return Math.max(sectionsViewport().height(), currentPage().sections().size() * 30);
	}

	private GlibRect sectionRowRect(int index) {
		GlibRect palette = paletteRect();
		return new GlibRect(palette.x() + 10, sectionsStartY() + index * 30 - paletteScroll.scrollOffset(), palette.width() - 20, 25);
	}

	private record WorkspaceEntry(int pageIndex, int sectionIndex, Entry entry, String context, boolean groupStart) {}
	private record EntryGroup(List<Entry> entries, boolean explicit) {}
	private record PlacedWorkspaceEntry(int entryIndex, WorkspaceEntry workspaceEntry, GlibRect worldRect,
			boolean root, GlibRect visibleWorldRect, String groupKey, int groupSize, boolean expanded) {}

	private static final class GroupAnimation {
		private final float from;
		private final boolean expanded;
		private final long startedAtNanos;

		private GroupAnimation(float from, boolean expanded, long startedAtNanos) {
			this.from = from;
			this.expanded = expanded;
			this.startedAtNanos = startedAtNanos;
		}

		private float value(long now) {
			float elapsed = (now - startedAtNanos) / 1_000_000_000.0F;
			float amount = Math.max(0.0F, Math.min(1.0F, elapsed / GROUP_ANIMATION_SECONDS));
			float target = expanded ? 1.0F : 0.0F;
			return GlibUi.lerp(from, target, amount);
		}
	}

	public record Page(Text title, Text subtitle, List<Section> sections) {
		public Page {
			title = Objects.requireNonNull(title, "title");
			subtitle = subtitle == null ? Text.empty() : subtitle;
			sections = sections == null ? List.of() : List.copyOf(sections);
		}
	}

	public record Section(Text title, Text description, List<Entry> entries) {
		public Section {
			title = Objects.requireNonNull(title, "title");
			description = description == null ? Text.empty() : description;
			entries = entries == null ? List.of() : List.copyOf(entries);
		}
	}

	public sealed interface Entry permits ToggleEntry, CycleEntry, SliderEntry, TextEntry, ActionEntry, LabelEntry {
		Text label();
		Text description();
		BooleanSupplier available();
	}

	public record ToggleEntry(Text label, Text description, BooleanSupplier available, Supplier<Boolean> value,
			Consumer<Boolean> setter, Function<Boolean, Text> formatter) implements Entry {}

	public record CycleEntry(Text label, Text description, BooleanSupplier available, Supplier<Text> valueText,
			Runnable cycle) implements Entry {}

	/** A precise numeric input. The legacy name is retained for source compatibility. */
	public record SliderEntry(Text label, Text description, BooleanSupplier available, DoubleSupplier value,
			DoubleConsumer setter, DoubleFunction<Text> valueText, double min, double max) implements Entry {
		public SliderEntry(Text label, Text description, BooleanSupplier available, DoubleSupplier value,
				DoubleConsumer setter, DoubleFunction<Text> valueText) {
			this(label, description, available, value, setter, valueText, 0.0D, 1.0D);
		}

		public SliderEntry {
			if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
				throw new IllegalArgumentException("Numeric input requires a finite min <= max");
			}
		}
	}

	public record TextEntry(Text label, Text description, BooleanSupplier available, Supplier<String> value,
			Consumer<String> setter, Predicate<String> validator, int maxLength) implements Entry {}

	public record ActionEntry(Text label, Text description, BooleanSupplier available, Text buttonText,
			Runnable action) implements Entry {}

	public record LabelEntry(Text label, Text description, BooleanSupplier available) implements Entry {}

	public static final class Builder {
		private final Screen parent;
		private final Text title;
		private Text subtitle = Text.empty();
		private int accentColor = 0xFF45D483;
		private final List<Page> pages = new ArrayList<>();
		private Runnable saveAction = () -> {};
		private Runnable cancelAction = () -> {};
		private boolean liveChanges;

		private Builder(Screen parent, Text title) {
			this.parent = parent;
			this.title = Objects.requireNonNull(title, "title");
		}

		public Builder subtitle(Text subtitle) { this.subtitle = subtitle == null ? Text.empty() : subtitle; return this; }
		public Builder accentColor(int accentColor) { this.accentColor = accentColor; return this; }
		public Builder page(Page page) { this.pages.add(Objects.requireNonNull(page, "page")); return this; }
		public Builder pages(List<Page> pages) { if (pages != null) this.pages.addAll(pages); return this; }
		public Builder onSave(Runnable saveAction) { this.saveAction = saveAction == null ? () -> {} : saveAction; return this; }
		public Builder onCancel(Runnable cancelAction) { this.cancelAction = cancelAction == null ? () -> {} : cancelAction; return this; }
		public Builder liveChanges() { this.liveChanges = true; return this; }
		public GexpressSettingsEditorScreen build() { return new GexpressSettingsEditorScreen(this); }
	}

}
