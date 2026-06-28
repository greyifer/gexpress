package dev.mapselect.client.screen;

import dev.mapselect.config.GexpressConfig;
import dev.mapselect.host.PlayerTag;
import dev.mapselect.host.PlayerTagComponent;
import dev.mapselect.permissions.GexpressPermissions;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GexpressTagEditorScreen extends Screen {
	private static final List<GexpressPermissions.PermissionEntry> PERMISSIONS = GexpressPermissions.permissionEntries();
	private static final List<String> PERMISSION_KEYS = GexpressPermissions.permissionKeys();
	private static final List<PermissionRow> PERMISSION_ROWS = buildPermissionRows();
	private static final int TAG_ROW_HEIGHT = 22;
	private static final int PERMISSION_ROW_HEIGHT = 31;
	private static final int PERMISSION_HEADER_HEIGHT = 17;
	private static final int SELECTED_PERMISSION_ROW_HEIGHT = 22;
	private final Screen parent;
	private TextFieldWidget idField;
	private TextFieldWidget nameField;
	private TextFieldWidget colorField;
	private TextFieldWidget priorityField;
	private TextFieldWidget permissionSearchField;
	private ButtonWidget deleteButton;
	private ButtonWidget playerTagsButton;
	private ButtonWidget levelTagsButton;
	private final Set<String> enabledPermissions = new LinkedHashSet<>();
	private Mode mode = Mode.PLAYER_TAGS;
	private String selectedId = "";
	private boolean selectedBuiltin;
	private float hue = 0.58F;
	private float saturation = 0.68F;
	private float value = 0.92F;
	private boolean draggingColor;
	private boolean draggingHue;
	private int tagListScroll;
	private int permissionScroll;
	private int addedPermissionScroll;

	public GexpressTagEditorScreen(Screen parent) {
		super(Text.translatable("gui.gexpress.tag_editor.title"));
		this.parent = parent;
	}

	private static List<PermissionRow> buildPermissionRows() {
		List<PermissionRow> rows = new ArrayList<>();
		String currentGroup = null;
		for (GexpressPermissions.PermissionEntry permission : PERMISSIONS) {
			if (!permission.group().equals(currentGroup)) {
				currentGroup = permission.group();
				rows.add(new PermissionRow(currentGroup, null));
			}
			rows.add(new PermissionRow(currentGroup, permission));
		}
		return List.copyOf(rows);
	}

	@Override
	protected void init() {
		int formX = formX();
		int formY = 54;
		int fieldWidth = Math.min(240, Math.max(128, contentWidth()));
		idField = addDrawableChild(new TextFieldWidget(textRenderer, formX, formY, fieldWidth, 18,
			Text.translatable("gui.gexpress.tag_editor.id")));
		nameField = addDrawableChild(new TextFieldWidget(textRenderer, formX, formY + 28, fieldWidth, 18,
			Text.translatable("gui.gexpress.tag_editor.name")));
		colorField = addDrawableChild(new TextFieldWidget(textRenderer, formX, formY + 56, fieldWidth, 18,
			Text.translatable("gui.gexpress.tag_editor.color")));
		priorityField = addDrawableChild(new TextFieldWidget(textRenderer, formX, formY + 84, fieldWidth, 18,
			Text.translatable("gui.gexpress.tag_editor.priority")));
		colorField.setText("#D36BFF");
		priorityField.setText("50");
		permissionSearchField = addDrawableChild(new TextFieldWidget(textRenderer, formX, permissionTop(),
			contentWidth(), 18, Text.literal("Search commands and permissions")));
		permissionSearchField.setMaxLength(96);

		playerTagsButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.tag_editor.player_tags"),
				button -> setMode(Mode.PLAYER_TAGS))
			.dimensions(18, 28, 92, 20)
			.build());
		levelTagsButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.tag_editor.level_tags"),
				button -> setMode(Mode.LEVEL_TAGS))
			.dimensions(114, 28, 92, 20)
			.build());

		int buttonY = Math.max(formY + 292, height - 58);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.tag_editor.save"), button -> save())
			.dimensions(formX, buttonY, 72, 20)
			.build());
		deleteButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.tag_editor.delete"),
				button -> delete())
			.dimensions(formX + 80, buttonY, 72, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.tag_editor.new"), button -> clearForNew())
			.dimensions(formX + 160, buttonY, 72, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
			.dimensions(width / 2 - 45, height - 28, 90, 20)
			.build());
		updateModeWidgets();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xAA11151B);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.translatable(mode == Mode.LEVEL_TAGS
				? "gui.gexpress.tag_editor.level_subtitle"
				: "gui.gexpress.tag_editor.subtitle").formatted(Formatting.GRAY),
			width / 2, 29, 0xFF9BA3AE);
		drawTagList(context, mouseX, mouseY);
		drawLabels(context);
		if (mode == Mode.PLAYER_TAGS) {
			drawPermissions(context, mouseX, mouseY);
		} else {
			drawLevelTagHelp(context);
		}
		drawColorPicker(context, mouseX, mouseY);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			if (clickTagList(mouseX, mouseY)
					|| (mode == Mode.PLAYER_TAGS && clickPermission(mouseX, mouseY))
					|| clickColor(mouseX, mouseY)) {
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && (draggingColor || draggingHue)) {
			updateColor(mouseX, mouseY, draggingHue);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingColor = false;
		draggingHue = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= 18 && mouseX < 18 + listWidth() && mouseY >= tagListTop() && mouseY < tagListBottom()) {
			tagListScroll = scrollBy(tagListScroll, verticalAmount, maxTagListScroll());
			return true;
		}
		if (mode == Mode.PLAYER_TAGS && mouseX >= formX() && mouseX < formX() + contentWidth()
				&& mouseY >= selectedPermissionTop() && mouseY < selectedPermissionBottom()) {
			addedPermissionScroll = scrollBy(addedPermissionScroll, verticalAmount, maxAddedPermissionScroll());
			return true;
		}
		if (mode == Mode.PLAYER_TAGS && mouseX >= formX() && mouseX < formX() + contentWidth()
				&& mouseY >= permissionResultTop() && mouseY < permissionBottom()) {
			permissionScroll = scrollBy(permissionScroll, verticalAmount, maxPermissionScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private void drawTagList(DrawContext context, int mouseX, int mouseY) {
		int x = 18;
		int y = tagListTop();
		int listWidth = listWidth();
		List<EditableTag> tags = visibleTags();
		tagListScroll = MathHelper.clamp(tagListScroll, 0, maxTagListScroll(tags.size()));
		context.drawTextWithShadow(textRenderer, Text.translatable(mode == Mode.LEVEL_TAGS
				? "gui.gexpress.tag_editor.level_tags"
				: "gui.gexpress.tag_editor.tags"), x, y - 14,
			0xFFFFFFFF);
		context.enableScissor(x, y, x + listWidth, tagListBottom());
		int row = 0;
		for (EditableTag tag : tags) {
			int ry = y + row * TAG_ROW_HEIGHT - tagListScroll;
			if (ry + 18 <= y || ry >= tagListBottom()) {
				row++;
				continue;
			}
			boolean selected = tag.id().equals(selectedId);
			boolean hovered = mouseX >= x && mouseX < x + listWidth && mouseY >= ry && mouseY < ry + 18;
			context.fill(x, ry, x + listWidth, ry + 18, selected ? 0xAA2D3542 : hovered ? 0x77313A48 : 0x55212833);
			context.fill(x, ry + 16, x + listWidth, ry + 18, 0xFF000000 | tag.color());
			String label = mode == Mode.LEVEL_TAGS ? "Level " + tag.id() + " - " + tag.displayName() : tag.displayName();
			context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(label, listWidth - 20)),
				x + 5, ry + 5, 0xFFFFFFFF);
			if (tag.builtin()) {
				context.drawTextWithShadow(textRenderer, Text.literal(mode == Mode.LEVEL_TAGS ? "L" : "*"),
					x + listWidth - 10, ry + 5, 0xFF9BA3AE);
			}
			row++;
		}
		context.disableScissor();
		drawScrollbar(context, x + listWidth + 3, y, tagListBottom(), tagListScroll, maxTagListScroll(tags.size()));
	}

	private boolean clickTagList(double mouseX, double mouseY) {
		int x = 18;
		int y = tagListTop();
		if (mouseX < x || mouseX >= x + listWidth() || mouseY < y || mouseY >= tagListBottom()) return false;
		int row = 0;
		for (EditableTag tag : visibleTags()) {
			int ry = y + row * TAG_ROW_HEIGHT - tagListScroll;
			if (mouseX >= x && mouseX < x + listWidth() && mouseY >= ry && mouseY < ry + 18) {
				load(tag);
				return true;
			}
			row++;
		}
		return false;
	}

	private void drawLabels(DrawContext context) {
		int labelX = formX();
		int y = 43;
		context.drawTextWithShadow(textRenderer, Text.literal(mode == Mode.LEVEL_TAGS ? "Level" : "Id"),
			labelX, y, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.literal("Name"), labelX, y + 28, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.literal("Color"), labelX, y + 56, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.literal("Priority"), labelX, y + 84, 0xFFB9C2CE);
	}

	private void drawLevelTagHelp(DrawContext context) {
		int x = formX();
		int y = 174;
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.tag_editor.level_tag_rules"),
			x, y - 14, 0xFFFFFFFF);
		List<Text> lines = List.of(
			Text.translatable("gui.gexpress.tag_editor.level_tag_rule_1"),
			Text.translatable("gui.gexpress.tag_editor.level_tag_rule_2"),
			Text.translatable("gui.gexpress.tag_editor.level_tag_rule_3")
		);
		for (int i = 0; i < lines.size(); i++) {
			context.drawTextWithShadow(textRenderer, lines.get(i).copy().formatted(Formatting.GRAY),
				x, y + i * 13, 0xFF9BA3AE);
		}
	}

	private void drawPermissions(DrawContext context, int mouseX, int mouseY) {
		int x = formX();
		int y = permissionTop();
		int bottom = permissionBottom();
		int selectedTop = selectedPermissionTop();
		int selectedBottom = selectedPermissionBottom();
		int resultTop = permissionResultTop();
		List<GexpressPermissions.PermissionEntry> selected = enabledPermissionEntries();
		List<GexpressPermissions.PermissionEntry> candidates = permissionCandidates();
		addedPermissionScroll = MathHelper.clamp(addedPermissionScroll, 0, maxAddedPermissionScroll(selected.size()));
		permissionScroll = MathHelper.clamp(permissionScroll, 0, maxPermissionScroll());
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.tag_editor.permissions"), x, y - 14,
			0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer,
			Text.literal("Added commands and access").formatted(Formatting.GRAY), x, selectedTop - 12,
			0xFF9BA3AE);
		context.enableScissor(x, selectedTop, x + contentWidth(), selectedBottom);
		if (selected.isEmpty()) {
			context.drawTextWithShadow(textRenderer,
				Text.literal("Nothing added yet.").formatted(Formatting.DARK_GRAY), x + 6, selectedTop + 6,
				0xFF777777);
		}
		for (int i = 0; i < selected.size(); i++) {
			GexpressPermissions.PermissionEntry entry = selected.get(i);
			int rowY = selectedTop + i * SELECTED_PERMISSION_ROW_HEIGHT - addedPermissionScroll;
			if (rowY + 18 <= selectedTop || rowY >= selectedBottom) continue;
			boolean hovered = mouseX >= x && mouseX < x + contentWidth() && mouseY >= rowY && mouseY < rowY + 18;
			context.fill(x, rowY, x + contentWidth(), rowY + 18, hovered ? 0xAA69403A : 0xAA2E6F57);
			context.drawBorder(x, rowY, contentWidth(), 18, hovered ? 0xFFFFA078 : 0xAA6BE3A7);
			String label = entry.label() + "  -  " + entry.key();
			context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(label, contentWidth() - 28)),
				x + 6, rowY + 5, 0xFFFFFFFF);
			context.drawTextWithShadow(textRenderer, Text.literal("x"), x + contentWidth() - 13, rowY + 5,
				0xFFFFB3A0);
		}
		context.disableScissor();
		drawScrollbar(context, x + contentWidth() + 3, selectedTop, selectedBottom, addedPermissionScroll,
			maxAddedPermissionScroll(selected.size()));

		context.drawTextWithShadow(textRenderer,
			Text.literal("Search results").formatted(Formatting.GRAY), x, resultTop - 12, 0xFF9BA3AE);
		context.enableScissor(x, resultTop, x + contentWidth(), bottom);
		if (candidates.isEmpty()) {
			context.drawTextWithShadow(textRenderer,
				Text.literal(permissionSearchField == null || permissionSearchField.getText().isBlank()
					? "All permissions are already added."
					: "No matching permissions.")
					.formatted(Formatting.DARK_GRAY),
				x + 6, resultTop + 6, 0xFF777777);
		}
		for (int i = 0; i < candidates.size(); i++) {
			GexpressPermissions.PermissionEntry entry = candidates.get(i);
			int rowY = resultTop + i * PERMISSION_ROW_HEIGHT - permissionScroll;
			if (rowY + PERMISSION_ROW_HEIGHT - 4 <= resultTop || rowY >= bottom) continue;
			boolean hovered = mouseX >= x && mouseX < x + contentWidth()
				&& mouseY >= rowY && mouseY < rowY + PERMISSION_ROW_HEIGHT - 4;
			context.fill(x, rowY, x + contentWidth(), rowY + PERMISSION_ROW_HEIGHT - 4,
				hovered ? 0x77313A48 : 0x55212833);
			context.drawBorder(x, rowY, contentWidth(), PERMISSION_ROW_HEIGHT - 4,
				hovered ? 0xAA8795A5 : 0x443C4A58);
			drawPermissionEntry(context, entry, x, rowY, contentWidth(), 0xFFFFFFFF);
		}
		context.disableScissor();
		drawScrollbar(context, x + contentWidth() + 3, resultTop, bottom, permissionScroll,
			maxPermissionScroll(candidates.size()));
	}

	private boolean clickPermission(double mouseX, double mouseY) {
		int x = formX();
		if (mouseX < x || mouseX >= x + contentWidth()) return false;
		if (mouseY >= selectedPermissionTop() && mouseY < selectedPermissionBottom()) {
			int index = ((int) mouseY - selectedPermissionTop() + addedPermissionScroll)
				/ SELECTED_PERMISSION_ROW_HEIGHT;
			List<GexpressPermissions.PermissionEntry> selected = enabledPermissionEntries();
			if (index >= 0 && index < selected.size()) {
				enabledPermissions.remove(selected.get(index).key());
				addedPermissionScroll = MathHelper.clamp(addedPermissionScroll, 0, maxAddedPermissionScroll());
				return true;
			}
		}
		if (mouseY >= permissionResultTop() && mouseY < permissionBottom()) {
			int index = ((int) mouseY - permissionResultTop() + permissionScroll) / PERMISSION_ROW_HEIGHT;
			List<GexpressPermissions.PermissionEntry> candidates = permissionCandidates();
			if (index >= 0 && index < candidates.size()) {
				enabledPermissions.add(candidates.get(index).key());
				permissionScroll = MathHelper.clamp(permissionScroll, 0, maxPermissionScroll());
				return true;
			}
		}
		return false;
	}

	private void drawPermissionEntry(DrawContext context, GexpressPermissions.PermissionEntry entry, int x, int y,
			int width, int labelColor) {
		String key = entry.key();
		int keyWidth = Math.min(textRenderer.getWidth(key), Math.max(68, width / 3));
		context.drawTextWithShadow(textRenderer,
			Text.literal(textRenderer.trimToWidth(entry.label(), width - keyWidth - 22)).formatted(Formatting.BOLD),
			x + 6, y + 4, labelColor);
		context.drawTextWithShadow(textRenderer,
			Text.literal(textRenderer.trimToWidth(key, keyWidth)).formatted(Formatting.DARK_GRAY),
			x + width - keyWidth - 6, y + 4, 0xFF7E8994);
		context.drawTextWithShadow(textRenderer,
			Text.literal(textRenderer.trimToWidth(entry.description(), width - 12)).formatted(Formatting.GRAY),
			x + 6, y + 17, 0xFF9BA3AE);
	}

	private List<GexpressPermissions.PermissionEntry> enabledPermissionEntries() {
		List<GexpressPermissions.PermissionEntry> out = new ArrayList<>();
		for (String key : enabledPermissions) {
			GexpressPermissions.PermissionEntry entry = permissionEntry(key);
			if (entry != null) out.add(entry);
		}
		return out;
	}

	private List<GexpressPermissions.PermissionEntry> permissionCandidates() {
		String query = permissionSearchField == null ? "" : permissionSearchField.getText().trim();
		List<GexpressPermissions.PermissionEntry> out = new ArrayList<>();
		for (GexpressPermissions.PermissionEntry entry : PERMISSIONS) {
			if (enabledPermissions.contains(entry.key())) continue;
			if (!permissionMatches(entry, query)) continue;
			out.add(entry);
		}
		return out;
	}

	private boolean permissionMatches(GexpressPermissions.PermissionEntry entry, String rawQuery) {
		if (entry == null) return false;
		String query = rawQuery == null ? "" : rawQuery.toLowerCase(Locale.ROOT).trim();
		if (query.isEmpty()) return true;
		String slashless = query.startsWith("/") ? query.substring(1) : query;
		return entry.key().toLowerCase(Locale.ROOT).contains(query)
			|| entry.label().toLowerCase(Locale.ROOT).contains(query)
			|| entry.group().toLowerCase(Locale.ROOT).contains(query)
			|| entry.description().toLowerCase(Locale.ROOT).contains(query)
			|| entry.label().toLowerCase(Locale.ROOT).replace("/", "").contains(slashless);
	}

	private GexpressPermissions.PermissionEntry permissionEntry(String key) {
		if (key == null) return null;
		for (GexpressPermissions.PermissionEntry entry : PERMISSIONS) {
			if (key.equals(entry.key())) return entry;
		}
		return null;
	}

	private void drawColorPicker(DrawContext context, int mouseX, int mouseY) {
		int x = pickerX();
		int y = 54;
		int w = pickerWidth();
		int h = 110;
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.tag_editor.color_picker"), x, y - 14,
			0xFFFFFFFF);
		int hueColor = 0xFF000000 | MathHelper.hsvToRgb(hue, 1.0F, 1.0F);
		final int swatchStep = 4;
		for (int ix = 0; ix < w; ix += swatchStep) {
			float sat = ix / (float) Math.max(1, w - 1);
			for (int iy = 0; iy < h; iy += swatchStep) {
				float val = 1.0F - iy / (float) Math.max(1, h - 1);
				context.fill(x + ix, y + iy, Math.min(x + ix + swatchStep, x + w),
					Math.min(y + iy + swatchStep, y + h),
					0xFF000000 | MathHelper.hsvToRgb(hue, sat, val));
			}
		}
		context.drawBorder(x, y, w, h, 0xFF617083);
		int cursorX = x + Math.round(saturation * (w - 1));
		int cursorY = y + Math.round((1.0F - value) * (h - 1));
		context.drawBorder(cursorX - 3, cursorY - 3, 7, 7, 0xFFFFFFFF);
		int sliderY = y + h + 20;
		for (int ix = 0; ix < w; ix += 2) {
			float localHue = ix / (float) Math.max(1, w - 1);
			context.fill(x + ix, sliderY, Math.min(x + ix + 2, x + w), sliderY + 8,
				0xFF000000 | MathHelper.hsvToRgb(localHue, 1.0F, 1.0F));
		}
		context.drawBorder(x, sliderY, w, 8, 0xFF617083);
		int hueX = x + Math.round(hue * (w - 1));
		context.fill(hueX - 2, sliderY - 3, hueX + 2, sliderY + 11, 0xFFFFFFFF);
		context.fill(x, sliderY + 20, x + 28, sliderY + 38, hueColor);
		context.drawTextWithShadow(textRenderer, Text.literal(colorField.getText()), x + 36, sliderY + 25,
			0xFFFFFFFF);
	}

	private boolean clickColor(double mouseX, double mouseY) {
		int x = pickerX();
		int y = 54;
		int w = pickerWidth();
		int h = 110;
		if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
			draggingColor = true;
			updateColor(mouseX, mouseY, false);
			return true;
		}
		int sliderY = y + h + 20;
		if (mouseX >= x && mouseX < x + w && mouseY >= sliderY - 3 && mouseY < sliderY + 12) {
			draggingHue = true;
			updateColor(mouseX, mouseY, true);
			return true;
		}
		return false;
	}

	private void updateColor(double mouseX, double mouseY, boolean hueOnly) {
		int x = pickerX();
		int y = 54;
		int w = pickerWidth();
		int h = 110;
		if (hueOnly) {
			hue = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - x) / Math.max(1.0D, w - 1.0D))));
		} else {
			saturation = Math.max(0.0F, Math.min(1.0F, (float) ((mouseX - x) / Math.max(1.0D, w - 1.0D))));
			value = 1.0F - Math.max(0.0F, Math.min(1.0F, (float) ((mouseY - y) / Math.max(1.0D, h - 1.0D))));
		}
		int color = MathHelper.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
		colorField.setText(String.format(Locale.ROOT, "#%06X", color));
	}

	private void load(EditableTag tag) {
		selectedId = tag.id();
		selectedBuiltin = tag.builtin();
		idField.setEditable(mode == Mode.LEVEL_TAGS || !selectedBuiltin);
		nameField.setEditable(mode == Mode.LEVEL_TAGS || !selectedBuiltin);
		idField.setText(tag.id());
		nameField.setText(tag.displayName());
		colorField.setText(String.format(Locale.ROOT, "#%06X", tag.color()));
		priorityField.setText(Integer.toString(tag.priority()));
		enabledPermissions.clear();
		for (String permission : tag.permissions()) {
			String key = GexpressPermissions.canonicalPermission(permission);
			if (key != null) enabledPermissions.add(key);
		}
		permissionScroll = 0;
		addedPermissionScroll = 0;
		if (deleteButton != null) {
			deleteButton.setMessage(Text.translatable(mode == Mode.LEVEL_TAGS
				? "gui.gexpress.tag_editor.delete"
				: selectedBuiltin
				? "gui.gexpress.tag_editor.reset"
				: "gui.gexpress.tag_editor.delete"));
		}
		syncHsvFromColor(tag.color());
	}

	private void clearForNew() {
		selectedId = "";
		selectedBuiltin = false;
		idField.setEditable(true);
		nameField.setEditable(true);
		idField.setText("");
		nameField.setText("");
		colorField.setText(mode == Mode.LEVEL_TAGS ? "#F2C94C" : "#D36BFF");
		priorityField.setText("50");
		enabledPermissions.clear();
		permissionScroll = 0;
		addedPermissionScroll = 0;
		if (permissionSearchField != null) permissionSearchField.setText("");
		if (deleteButton != null) {
			deleteButton.setMessage(Text.translatable("gui.gexpress.tag_editor.delete"));
		}
		syncHsvFromColor(mode == Mode.LEVEL_TAGS ? 0xF2C94C : 0xD36BFF);
	}

	private void save() {
		if (mode == Mode.LEVEL_TAGS) {
			saveLevelTag();
			return;
		}
		String id = PlayerTagComponent.normalizeCustomId(idField.getText());
		if (id == null) return;
		String color = normalizeHex(colorField.getText());
		int priority = parsePriority();
		if (selectedBuiltin) {
			send("g admin tag settings color " + id + " " + color);
			send("g admin tag settings priority " + id + " " + priority);
			for (String permission : PERMISSION_KEYS) {
				send("g admin tag settings permission " + id + " " + permission + " "
					+ enabledPermissions.contains(permission));
			}
			selectedId = id;
			return;
		}
		String name = nameField.getText().isBlank() ? id : nameField.getText().trim().replace(' ', '_');
		send("g admin tag custom create " + id + " " + name + " " + color + " " + priority);
		send("g admin tag custom name " + id + " " + name);
		send("g admin tag custom color " + id + " " + color);
		send("g admin tag custom priority " + id + " " + priority);
		for (String permission : PERMISSION_KEYS) {
			send("g admin tag custom permission " + id + " " + permission + " "
				+ enabledPermissions.contains(permission));
		}
		selectedId = id;
	}

	private void delete() {
		if (mode == Mode.LEVEL_TAGS) {
			deleteLevelTag();
			return;
		}
		String id = PlayerTagComponent.normalizeCustomId(idField.getText());
		if (id == null) return;
		if (selectedBuiltin) {
			send("g admin tag settings reset " + id);
			PlayerTag builtin = PlayerTag.byId(id);
			if (builtin != null) load(EditableTag.from(builtin, null));
			return;
		}
		send("g admin tag custom delete " + id);
		selectedId = "";
		idField.setText("");
		nameField.setText("");
		enabledPermissions.clear();
	}

	private int parsePriority() {
		try {
			return Math.max(0, Math.min(200, Integer.parseInt(priorityField.getText().trim())));
		} catch (NumberFormatException ignored) {
			return 50;
		}
	}

	private String normalizeHex(String raw) {
		String value = raw == null ? "" : raw.trim();
		if (value.startsWith("#")) value = value.substring(1);
		if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
		if (value.length() != 6 || !value.matches("[0-9a-fA-F]{6}")) return "D36BFF";
		return value.toUpperCase(Locale.ROOT);
	}

	private List<EditableTag> visibleTags() {
		if (mode == Mode.LEVEL_TAGS) {
			return visibleLevelTags();
		}
		PlayerTagComponent component = component();
		List<EditableTag> out = new ArrayList<>();
		for (PlayerTag tag : PlayerTag.values()) out.add(EditableTag.from(tag, component));
		if (component != null) {
			component.getCustomTags().values().stream()
				.map(EditableTag::from)
				.forEach(out::add);
		}
		out.sort(Comparator.comparingInt(EditableTag::priority).reversed()
			.thenComparing(EditableTag::displayName, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private List<EditableTag> visibleLevelTags() {
		List<EditableTag> out = new ArrayList<>();
		for (GexpressConfig.LevelTagEntry tag : GexpressConfig.getLevelTagEntries()) {
			out.add(new EditableTag(Integer.toString(tag.level()), tag.displayName(), tag.color(),
				tag.priority(), true, Set.of()));
		}
		out.sort(Comparator.comparingInt((EditableTag tag) -> parseLevel(tag.id())).reversed()
			.thenComparing(EditableTag::displayName, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private void saveLevelTag() {
		int level = parseLevel(idField.getText());
		if (level <= 0) return;
		String name = nameField.getText().isBlank() ? "Level " + level : nameField.getText().trim();
		String color = normalizeHex(colorField.getText());
		int priority = parsePriority();
		List<String> rows = new ArrayList<>(GexpressConfig.getLevelTagStrings());
		rows.removeIf(row -> {
			GexpressConfig.LevelTagEntry existing = parseLevelTag(row);
			return existing != null && existing.level() == level;
		});
		rows.add(level + "|" + name.replace("|", "") + "|#" + color + "|" + priority);
		GexpressConfig.setLevelTagStrings(rows);
		GexpressOptionsScreen.pushGexpressConfigToServer();
		selectedId = Integer.toString(level);
	}

	private void deleteLevelTag() {
		int level = parseLevel(idField.getText());
		if (level <= 0) return;
		List<String> rows = new ArrayList<>(GexpressConfig.getLevelTagStrings());
		rows.removeIf(row -> {
			GexpressConfig.LevelTagEntry existing = parseLevelTag(row);
			return existing != null && existing.level() == level;
		});
		GexpressConfig.setLevelTagStrings(rows);
		GexpressOptionsScreen.pushGexpressConfigToServer();
		clearForNew();
	}

	private GexpressConfig.LevelTagEntry parseLevelTag(String row) {
		if (row == null || row.isBlank()) return null;
		String[] parts = row.split("\\|", 4);
		if (parts.length < 2) return null;
		int level = parseLevel(parts[0]);
		if (level <= 0) return null;
		String name = parts[1].strip();
		if (name.isEmpty()) return null;
		int color = parts.length >= 3 ? parseColor(parts[2], 0xF2C94C) : 0xF2C94C;
		int priority = parts.length >= 4 ? parseBoundedInt(parts[3], 50, 0, 200) : 50;
		return new GexpressConfig.LevelTagEntry(level, name, color, priority);
	}

	private int parseLevel(String raw) {
		try {
			return Math.max(0, Math.min(999, Integer.parseInt(raw == null ? "" : raw.trim())));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private int parseColor(String raw, int fallback) {
		String value = normalizeHex(raw);
		try {
			return Integer.parseInt(value, 16) & 0xFFFFFF;
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private int parseBoundedInt(String raw, int fallback, int min, int max) {
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(raw == null ? "" : raw.trim())));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private int listWidth() {
		return MathHelper.clamp(width / 5, 142, 190);
	}

	private int formX() {
		return 18 + listWidth() + 24;
	}

	private int pickerWidth() {
		return MathHelper.clamp(width / 4, 180, 260);
	}

	private int pickerX() {
		return width - pickerWidth() - 18;
	}

	private int contentWidth() {
		return Math.max(96, pickerX() - formX() - 16);
	}

	private int permissionColumns() {
		return contentWidth() >= 320 ? 2 : 1;
	}

	private int tagListTop() {
		return 52;
	}

	private int tagListBottom() {
		return Math.max(tagListTop() + 24, height - 44);
	}

	private int permissionTop() {
		return 174;
	}

	private int selectedPermissionTop() {
		return permissionTop() + 42;
	}

	private int selectedPermissionBottom() {
		return Math.min(permissionTop() + 124, Math.max(selectedPermissionTop() + 24, permissionBottom() - 104));
	}

	private int permissionResultTop() {
		return selectedPermissionBottom() + 24;
	}

	private int permissionBottom() {
		return Math.max(permissionTop() + 24, height - 70);
	}

	private int maxTagListScroll() {
		return maxTagListScroll(visibleTags().size());
	}

	private int maxTagListScroll(int size) {
		return Math.max(0, size * TAG_ROW_HEIGHT - (tagListBottom() - tagListTop()));
	}

	private int maxPermissionScroll() {
		return maxPermissionScroll(permissionCandidates().size());
	}

	private int maxPermissionScroll(int size) {
		return Math.max(0, size * PERMISSION_ROW_HEIGHT - (permissionBottom() - permissionResultTop()));
	}

	private int maxAddedPermissionScroll() {
		return maxAddedPermissionScroll(enabledPermissionEntries().size());
	}

	private int maxAddedPermissionScroll(int size) {
		return Math.max(0, size * SELECTED_PERMISSION_ROW_HEIGHT
			- (selectedPermissionBottom() - selectedPermissionTop()));
	}

	private int scrollBy(int current, double verticalAmount, int max) {
		return MathHelper.clamp(current - (int) Math.signum(verticalAmount) * TAG_ROW_HEIGHT, 0, max);
	}

	private void drawScrollbar(DrawContext context, int x, int top, int bottom, int scroll, int maxScroll) {
		if (maxScroll <= 0 || bottom <= top) return;
		int trackH = bottom - top;
		int thumbH = Math.max(18, trackH * trackH / Math.max(trackH, trackH + maxScroll));
		int thumbY = top + (trackH - thumbH) * scroll / maxScroll;
		context.fill(x, top, x + 2, bottom, 0x553C4A58);
		context.fill(x, thumbY, x + 2, thumbY + thumbH, 0xFFBFA35A);
	}

	private void setMode(Mode next) {
		if (mode == next) return;
		mode = next;
		tagListScroll = 0;
		permissionScroll = 0;
		addedPermissionScroll = 0;
		clearForNew();
		updateModeWidgets();
	}

	private void updateModeWidgets() {
		if (playerTagsButton != null) playerTagsButton.active = mode != Mode.PLAYER_TAGS;
		if (levelTagsButton != null) levelTagsButton.active = mode != Mode.LEVEL_TAGS;
		if (permissionSearchField != null) {
			permissionSearchField.visible = mode == Mode.PLAYER_TAGS;
			permissionSearchField.active = mode == Mode.PLAYER_TAGS;
		}
	}

	private void syncHsvFromColor(int color) {
		float r = ((color >> 16) & 0xFF) / 255.0F;
		float g = ((color >> 8) & 0xFF) / 255.0F;
		float b = (color & 0xFF) / 255.0F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		value = max;
		saturation = max <= 0.0F ? 0.0F : delta / max;
		if (delta <= 0.0F) {
			hue = 0.0F;
		} else if (max == r) {
			hue = ((g - b) / delta) / 6.0F;
		} else if (max == g) {
			hue = (2.0F + (b - r) / delta) / 6.0F;
		} else {
			hue = (4.0F + (r - g) / delta) / 6.0F;
		}
		if (hue < 0.0F) hue += 1.0F;
	}

	private void send(String command) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null && client.player.networkHandler != null) {
			client.player.networkHandler.sendChatCommand(command);
		}
	}

	private PlayerTagComponent component() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client == null || client.world == null ? null : PlayerTagComponent.KEY.getNullable(client.world);
	}

	private record EditableTag(String id, String displayName, int color, int priority, boolean builtin,
			Set<String> permissions) {
		private static EditableTag from(PlayerTag tag, PlayerTagComponent component) {
			int color = component == null ? tag.color() : component.color(tag);
			int priority = component == null ? tag.priority() : component.priority(tag);
			Set<String> permissions = component == null ? Set.of() : component.permissions(tag);
			return new EditableTag(tag.id(), tag.displayName(), color, priority, true, Set.copyOf(permissions));
		}

		private static EditableTag from(PlayerTagComponent.CustomTag tag) {
			return new EditableTag(tag.id(), tag.displayName(), tag.color(), tag.priority(), false,
				Set.copyOf(tag.permissions()));
		}
	}

	private record PermissionRow(String group, GexpressPermissions.PermissionEntry permission) {
		private boolean isHeader() {
			return permission == null;
		}

		private int height() {
			return isHeader() ? PERMISSION_HEADER_HEIGHT : PERMISSION_ROW_HEIGHT;
		}
	}

	private enum Mode {
		PLAYER_TAGS,
		LEVEL_TAGS
	}
}
