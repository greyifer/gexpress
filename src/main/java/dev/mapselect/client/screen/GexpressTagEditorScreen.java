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
	private static final List<String> PERMISSIONS = GexpressPermissions.permissionKeys();
	private final Screen parent;
	private TextFieldWidget idField;
	private TextFieldWidget nameField;
	private TextFieldWidget colorField;
	private TextFieldWidget priorityField;
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

	public GexpressTagEditorScreen(Screen parent) {
		super(Text.translatable("gui.gexpress.tag_editor.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int formX = 190;
		int formY = 54;
		idField = addDrawableChild(new TextFieldWidget(textRenderer, formX, formY, 128, 18,
			Text.translatable("gui.gexpress.tag_editor.id")));
		nameField = addDrawableChild(new TextFieldWidget(textRenderer, formX, formY + 28, 128, 18,
			Text.translatable("gui.gexpress.tag_editor.name")));
		colorField = addDrawableChild(new TextFieldWidget(textRenderer, formX, formY + 56, 128, 18,
			Text.translatable("gui.gexpress.tag_editor.color")));
		priorityField = addDrawableChild(new TextFieldWidget(textRenderer, formX, formY + 84, 128, 18,
			Text.translatable("gui.gexpress.tag_editor.priority")));
		colorField.setText("#D36BFF");
		priorityField.setText("50");

		playerTagsButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.tag_editor.player_tags"),
				button -> setMode(Mode.PLAYER_TAGS))
			.dimensions(18, 28, 92, 20)
			.build());
		levelTagsButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.tag_editor.level_tags"),
				button -> setMode(Mode.LEVEL_TAGS))
			.dimensions(114, 28, 92, 20)
			.build());

		int buttonY = Math.min(height - 58, formY + 292);
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

	private void drawTagList(DrawContext context, int mouseX, int mouseY) {
		int x = 18;
		int y = 52;
		context.drawTextWithShadow(textRenderer, Text.translatable(mode == Mode.LEVEL_TAGS
				? "gui.gexpress.tag_editor.level_tags"
				: "gui.gexpress.tag_editor.tags"), x, y - 14,
			0xFFFFFFFF);
		int row = 0;
		for (EditableTag tag : visibleTags()) {
			int ry = y + row * 22;
			boolean selected = tag.id().equals(selectedId);
			boolean hovered = mouseX >= x && mouseX < x + 142 && mouseY >= ry && mouseY < ry + 18;
			context.fill(x, ry, x + 142, ry + 18, selected ? 0xAA2D3542 : hovered ? 0x77313A48 : 0x55212833);
			context.fill(x, ry + 16, x + 142, ry + 18, 0xFF000000 | tag.color());
			String label = mode == Mode.LEVEL_TAGS ? "Level " + tag.id() + " - " + tag.displayName() : tag.displayName();
			context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(label, 122)),
				x + 5, ry + 5, 0xFFFFFFFF);
			if (tag.builtin()) {
				context.drawTextWithShadow(textRenderer, Text.literal(mode == Mode.LEVEL_TAGS ? "L" : "*"),
					x + 132, ry + 5, 0xFF9BA3AE);
			}
			row++;
		}
	}

	private boolean clickTagList(double mouseX, double mouseY) {
		int x = 18;
		int y = 52;
		int row = 0;
		for (EditableTag tag : visibleTags()) {
			int ry = y + row * 22;
			if (mouseX >= x && mouseX < x + 142 && mouseY >= ry && mouseY < ry + 18) {
				load(tag);
				return true;
			}
			row++;
		}
		return false;
	}

	private void drawLabels(DrawContext context) {
		int labelX = 190;
		int y = 43;
		context.drawTextWithShadow(textRenderer, Text.literal(mode == Mode.LEVEL_TAGS ? "Level" : "Id"),
			labelX, y, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.literal("Name"), labelX, y + 28, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.literal("Color"), labelX, y + 56, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.literal("Priority"), labelX, y + 84, 0xFFB9C2CE);
	}

	private void drawLevelTagHelp(DrawContext context) {
		int x = 190;
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
		int x = 190;
		int y = 174;
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.gexpress.tag_editor.permissions"), x, y - 14,
			0xFFFFFFFF);
		for (int i = 0; i < PERMISSIONS.size(); i++) {
			int bx = x + (i % 2) * 172;
			int by = y + (i / 2) * 28;
			String permission = PERMISSIONS.get(i);
			boolean selected = enabledPermissions.contains(permission);
			boolean hovered = mouseX >= bx && mouseX < bx + 164 && mouseY >= by && mouseY < by + 24;
			context.fill(bx, by, bx + 164, by + 24, selected ? 0xAA2E6F57 : hovered ? 0x77313A48 : 0x55212833);
			context.drawBorder(bx, by, 164, 24, selected ? 0xAA6BE3A7 : 0x443C4A58);
			context.drawTextWithShadow(textRenderer, Text.literal(permission), bx + 5, by + 3,
				selected ? 0xFFB8FFD7 : 0xFFFFFFFF);
			String description = GexpressPermissions.permissionDescription(permission);
			if (description != null) {
				context.drawTextWithShadow(textRenderer,
					Text.literal(textRenderer.trimToWidth(description, 154)).formatted(Formatting.GRAY),
					bx + 5, by + 14, 0xFF9BA3AE);
			}
		}
	}

	private boolean clickPermission(double mouseX, double mouseY) {
		int x = 190;
		int y = 174;
		for (int i = 0; i < PERMISSIONS.size(); i++) {
			int bx = x + (i % 2) * 172;
			int by = y + (i / 2) * 28;
			if (mouseX < bx || mouseX >= bx + 164 || mouseY < by || mouseY >= by + 24) continue;
			String permission = PERMISSIONS.get(i);
			if (!enabledPermissions.remove(permission)) enabledPermissions.add(permission);
			return true;
		}
		return false;
	}

	private void drawColorPicker(DrawContext context, int mouseX, int mouseY) {
		int x = Math.max(370, width - 300);
		int y = 54;
		int w = 260;
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
		int x = Math.max(370, width - 300);
		int y = 54;
		int w = 260;
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
		int x = Math.max(370, width - 300);
		int y = 54;
		int w = 260;
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
			for (String permission : PERMISSIONS) {
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
		for (String permission : PERMISSIONS) {
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

	private void setMode(Mode next) {
		if (mode == next) return;
		mode = next;
		clearForNew();
		updateModeWidgets();
	}

	private void updateModeWidgets() {
		if (playerTagsButton != null) playerTagsButton.active = mode != Mode.PLAYER_TAGS;
		if (levelTagsButton != null) levelTagsButton.active = mode != Mode.LEVEL_TAGS;
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

	private enum Mode {
		PLAYER_TAGS,
		LEVEL_TAGS
	}
}
