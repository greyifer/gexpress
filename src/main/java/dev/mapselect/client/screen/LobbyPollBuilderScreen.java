package dev.mapselect.client.screen;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.mapselect.network.poll.LobbyPollCreatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LobbyPollBuilderScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private final Screen parent;
	private TextFieldWidget searchField;
	private TextFieldWidget valueField;
	private ButtonWidget roleButton;
	private ButtonWidget modifierButton;
	private ButtonWidget enableButton;
	private ButtonWidget disableButton;
	private ButtonWidget chanceButton;
	private ButtonWidget amountButton;
	private ButtonWidget submitButton;
	private String targetType = "role";
	private String setting = "enabled";
	private boolean enabledValue = true;
	private String selectedId = "";
	private int scroll;

	public LobbyPollBuilderScreen(Screen parent) {
		super(Text.literal("Create Lobby Poll"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cardX = cardX();
		int cardY = cardY();
		int cardW = cardW();
		searchField = addDrawableChild(new TextFieldWidget(textRenderer, cardX + 18, cardY + 96,
			cardW - 36, 20, Text.literal("Search")));
		searchField.setMaxLength(96);
		valueField = addDrawableChild(new TextFieldWidget(textRenderer, cardX + cardW - 118, cardY + cardH() - 70,
			100, 20, Text.literal("Value")));
		valueField.setMaxLength(4);
		valueField.setText("1");

		roleButton = addDrawableChild(ButtonWidget.builder(Text.literal("Role"), button -> selectTargetType("role"))
			.dimensions(cardX + 18, cardY + 52, 96, 20).build());
		modifierButton = addDrawableChild(ButtonWidget.builder(Text.literal("Modifier"), button -> selectTargetType("modifier"))
			.dimensions(cardX + 120, cardY + 52, 116, 20).build());
		enableButton = addDrawableChild(ButtonWidget.builder(Text.literal("Enable"), button -> selectEnabled(true))
			.dimensions(cardX + 18, cardY + cardH() - 70, 90, 20).build());
		disableButton = addDrawableChild(ButtonWidget.builder(Text.literal("Disable"), button -> selectEnabled(false))
			.dimensions(cardX + 114, cardY + cardH() - 70, 90, 20).build());
		chanceButton = addDrawableChild(ButtonWidget.builder(Text.literal("Set Chance"), button -> selectNumeric("chance", 100))
			.dimensions(cardX + 210, cardY + cardH() - 70, 104, 20).build());
		amountButton = addDrawableChild(ButtonWidget.builder(Text.literal("Set Amount"), button -> selectNumeric("amount", 1))
			.dimensions(cardX + 320, cardY + cardH() - 70, 110, 20).build());
		submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Create Poll"), button -> submit())
			.dimensions(cardX + cardW / 2 - 58, cardY + cardH() - 30, 116, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(Text.literal("X"), button -> close())
			.dimensions(cardX + cardW - 42, cardY + 12, 24, 20)
			.build());
		syncWidgetState();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		syncWidgetState();
		context.fill(0, 0, width, height, 0xAA06080C);
		int cardW = cardW();
		int cardX = cardX();
		int cardY = cardY();
		int cardH = cardH();
		context.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xEE151B24);
		context.drawBorder(cardX, cardY, cardW, cardH, 0xAAE7EEF8);
		context.drawTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), cardX + 18, cardY + 16,
			0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.literal(stateLabel()).formatted(Formatting.GRAY),
			cardX + 18, cardY + 31, 0xFFB9C4D0);
		context.drawTextWithShadow(textRenderer, Text.literal("Target type").formatted(Formatting.GRAY),
			cardX + 18, cardY + 40, 0xFF9EACB9);
		context.drawTextWithShadow(textRenderer, Text.literal("Search").formatted(Formatting.GRAY),
			cardX + 18, cardY + 84, 0xFF9EACB9);
		context.drawTextWithShadow(textRenderer, Text.literal("Change").formatted(Formatting.GRAY),
			cardX + 18, cardY + cardH - 82, 0xFF9EACB9);
		if (valueField != null && valueField.visible) {
			context.drawTextWithShadow(textRenderer, Text.literal("Value").formatted(Formatting.GRAY),
				cardX + cardW - 118, cardY + cardH - 82, 0xFF9EACB9);
		}
		drawList(context, listX(), listY(), listW(), listH(), mouseX, mouseY);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && clickList(mouseX, mouseY)) return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= listX() && mouseX < listX() + listW()
				&& mouseY >= listY() && mouseY < listY() + listH()) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount) * ROW_HEIGHT));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void submit() {
		if (selectedId.isBlank()) {
			if (client != null && client.player != null) {
				client.player.sendMessage(Text.literal("Pick a role or modifier first.").formatted(Formatting.RED),
					false);
			}
			return;
		}
		if (!ClientPlayNetworking.canSend(LobbyPollCreatePayload.ID)) return;
		ClientPlayNetworking.send(new LobbyPollCreatePayload(targetType, selectedId, setting, parseValue()));
		close();
	}

	private int parseValue() {
		if (setting.equals("enabled")) return enabledValue ? 1 : 0;
		try {
			return Integer.parseInt(valueField.getText().trim());
		} catch (NumberFormatException ignored) {
			return setting.equals("chance") ? 100 : 1;
		}
	}

	private String stateLabel() {
		String selected = selectedId.isBlank() ? "Nothing selected yet" : label(selectedId);
		String action = setting.equals("enabled")
			? (enabledValue ? "Enable" : "Disable")
			: (setting.equals("chance") ? "Set chance to " : "Set amount to ") + (valueField == null ? "" : valueField.getText());
		return action + " - " + selected;
	}

	private void drawList(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
		List<Entry> entries = filteredEntries();
		context.enableScissor(x, y, x + w, y + h);
		int row = 0;
		for (Entry entry : entries) {
			int rowY = y + row * ROW_HEIGHT - scroll;
			if (rowY + 20 <= y || rowY >= y + h) {
				row++;
				continue;
			}
			boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + 20;
			boolean selected = entry.id.equals(selectedId);
			context.fill(x, rowY, x + w, rowY + 20, selected ? 0xAA334A5F : hovered ? 0x77313A48 : 0x55212833);
			context.drawBorder(x, rowY, w, 20, selected ? 0xFFE0B65A : 0x553C4A58);
			context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(entry.name, w - 152)),
				x + 6, rowY + 6, 0xFFFFFFFF);
			context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(entry.id, 140))
				.formatted(Formatting.GRAY), x + w - 146, rowY + 6, 0xFF9EACB9);
			row++;
		}
		context.disableScissor();
		if (entries.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.literal("No matches").formatted(Formatting.GRAY),
				x + 6, y + 8, 0xFF9EACB9);
		}
	}

	private boolean clickList(double mouseX, double mouseY) {
		int x = listX();
		int y = listY();
		int w = listW();
		int h = listH();
		if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return false;
		int index = (int) ((mouseY - y + scroll) / ROW_HEIGHT);
		List<Entry> entries = filteredEntries();
		if (index < 0 || index >= entries.size()) return false;
		selectedId = entries.get(index).id;
		syncWidgetState();
		return true;
	}

	private int maxScroll() {
		return Math.max(0, filteredEntries().size() * ROW_HEIGHT - listH());
	}

	private void selectTargetType(String type) {
		if (!targetType.equals(type)) {
			targetType = type;
			selectedId = "";
			scroll = 0;
			if (searchField != null) searchField.setText("");
		}
		syncWidgetState();
	}

	private void selectEnabled(boolean enabled) {
		setting = "enabled";
		enabledValue = enabled;
		if (valueField != null) valueField.setText(enabled ? "1" : "0");
		syncWidgetState();
	}

	private void selectNumeric(String newSetting, int fallback) {
		setting = newSetting;
		if (valueField != null && parseValue() <= 1) valueField.setText(String.valueOf(fallback));
		syncWidgetState();
	}

	private void syncWidgetState() {
		if (roleButton != null) roleButton.setMessage(Text.literal(targetType.equals("role") ? "> Role" : "Role"));
		if (modifierButton != null) modifierButton.setMessage(Text.literal(targetType.equals("modifier") ? "> Modifier" : "Modifier"));
		if (enableButton != null) enableButton.setMessage(Text.literal(setting.equals("enabled") && enabledValue ? "> Enable" : "Enable"));
		if (disableButton != null) disableButton.setMessage(Text.literal(setting.equals("enabled") && !enabledValue ? "> Disable" : "Disable"));
		if (chanceButton != null) chanceButton.setMessage(Text.literal(setting.equals("chance") ? "> Set Chance" : "Set Chance"));
		if (amountButton != null) amountButton.setMessage(Text.literal(setting.equals("amount") ? "> Set Amount" : "Set Amount"));
		boolean numeric = setting.equals("chance") || setting.equals("amount");
		if (valueField != null) {
			valueField.visible = numeric;
			valueField.active = numeric;
		}
		if (submitButton != null) submitButton.active = !selectedId.isBlank();
	}

	private int cardW() {
		return Math.min(560, width - 32);
	}

	private int cardH() {
		return Math.min(360, height - 28);
	}

	private int cardX() {
		return (width - cardW()) / 2;
	}

	private int cardY() {
		return Math.max(14, (height - cardH()) / 2);
	}

	private int listX() {
		return cardX() + 18;
	}

	private int listY() {
		return cardY() + 122;
	}

	private int listW() {
		return cardW() - 36;
	}

	private int listH() {
		return Math.max(68, cardY() + cardH() - 92 - listY());
	}

	private List<Entry> filteredEntries() {
		String query = searchField == null ? "" : searchField.getText().toLowerCase(Locale.ROOT).trim();
		List<Entry> out = new ArrayList<>();
		if (targetType.equals("role")) {
			for (Role role : WatheRoles.ROLES) {
				if (role == null || role.identifier() == null) continue;
				addIfMatches(out, role.identifier().toString(), query);
			}
		} else {
			for (Modifier modifier : HMLModifiers.MODIFIERS) {
				if (modifier == null || modifier.identifier() == null) continue;
				addIfMatches(out, modifier.identifier().toString(), query);
			}
		}
		out.sort(Comparator.comparing(entry -> entry.name));
		return out;
	}

	private void addIfMatches(List<Entry> entries, String id, String query) {
		String name = label(id);
		if (query.isBlank() || id.toLowerCase(Locale.ROOT).contains(query)
				|| name.toLowerCase(Locale.ROOT).contains(query)) {
			entries.add(new Entry(id, name));
		}
	}

	private static String label(String id) {
		String path = id;
		int colon = path.indexOf(':');
		if (colon >= 0 && colon + 1 < path.length()) path = path.substring(colon + 1);
		String[] parts = path.replace('-', '_').split("_");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			if (part.length() > 1) out.append(part.substring(1));
		}
		return out.isEmpty() ? id : out.toString();
	}

	private record Entry(String id, String name) {}
}
