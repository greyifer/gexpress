package dev.mapselect.client.screen;

import dev.mapselect.MapSelect;
import dev.mapselect.client.text.GexpressRoleTexts;
import dev.mapselect.network.role.painter.PainterChoiceOpenPayload;
import dev.mapselect.network.role.painter.PainterChoiceSubmitPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PainterChoiceScreen extends Screen {
	private static final Identifier SLOT_TEXTURE = Identifier.of(MapSelect.MOD_ID, "textures/gui/painter_slot.png");
	private static final int GOLD = 0xFFFFB629;
	private static final int TEXT = 0xFFF4ECFF;
	private static final int MUTED = 0xFFBFAFD4;
	private static final int ROW_H = 20;
	private static final int SLOT = 32;
	private static final int SLOT_GAP = 16;

	private final PainterChoiceOpenPayload.Mode mode;
	private final UUID targetId;
	private final List<PainterChoiceOpenPayload.DeathChoice> deathChoices;
	private final List<PainterChoiceOpenPayload.RoleChoice> roleChoices;
	private final List<PainterChoiceOpenPayload.RoleChoice> filteredRoles = new ArrayList<>();
	private Step step;
	private Identifier selectedDeathReason;
	private TextFieldWidget searchField;
	private int roleScroll;
	private String filter = "";

	public PainterChoiceScreen(PainterChoiceOpenPayload payload) {
		super(Text.literal(payload.mode() == PainterChoiceOpenPayload.Mode.BODY ? "Corpse Paint" : "Living Paint"));
		this.mode = payload.mode();
		this.targetId = payload.targetId();
		this.deathChoices = payload.deathChoices();
		this.roleChoices = payload.roleChoices();
		this.step = mode == PainterChoiceOpenPayload.Mode.BODY ? Step.DEATH : Step.ROLE;
		refilter();
	}

	@Override
	protected void init() {
		searchField = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 220, 20, Text.literal("Search roles")));
		searchField.setMaxLength(48);
		searchField.setChangedListener(value -> {
			filter = value == null ? "" : value;
			roleScroll = 0;
			refilter();
		});
		layoutWidgets();
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		layoutWidgets();
		if (step == Step.DEATH) {
			drawDeathChoices(context, mouseX, mouseY);
		} else {
			drawRoleChoices(context, mouseX, mouseY);
		}
		super.render(context, mouseX, mouseY, delta);
		drawHoveredTooltip(context, mouseX, mouseY);
	}

	private void drawDeathChoices(DrawContext context, int mouseX, int mouseY) {
		int cols = deathColumns();
		int rows = deathRows(cols);
		int totalW = cols * SLOT + Math.max(0, cols - 1) * SLOT_GAP;
		int x = width / 2 - totalW / 2;
		int y = deathY(rows);
		Text prompt = GexpressRoleTexts.rainbow("Select the reason of the death.");
		context.drawTextWithShadow(textRenderer, prompt, width / 2 - textRenderer.getWidth(prompt) / 2, y - 22, TEXT);
		for (int i = 0; i < deathChoices.size(); i++) {
			PainterChoiceOpenPayload.DeathChoice choice = deathChoices.get(i);
			int col = i % cols;
			int row = i / cols;
			int cellX = x + col * (SLOT + SLOT_GAP);
			int cellY = y + row * (SLOT + 14);
			boolean hovered = inside(mouseX, mouseY, cellX, cellY, SLOT, SLOT);
			ItemStack stack = itemStack(choice.itemId(), choice.displayName());
			drawSlot(context, cellX, cellY);
			context.drawItem(stack, cellX + 8, cellY + 8);
			if (hovered) HandledScreen.drawSlotHighlight(context, cellX + 8, cellY + 8, 0);
		}
	}

	private void drawRoleChoices(DrawContext context, int mouseX, int mouseY) {
		int w = roleBoxWidth();
		int x = roleBoxX(w);
		int y = roleBoxY();
		int listTop = y + 22;
		int visibleRows = visibleRoleRows();
		int listBottom = listTop + visibleRows * ROW_H;
		context.fill(x - 1, y - 1, x + w + 1, listBottom + 1, 0xE3120700);
		context.drawBorder(x - 1, y - 1, w + 2, listBottom - y + 2, GOLD);
		if (mode == PainterChoiceOpenPayload.Mode.BODY && selectedDeathReason != null) {
			Text prompt = GexpressRoleTexts.rainbow("Select the fake role.");
			context.drawTextWithShadow(textRenderer, prompt, width / 2 - textRenderer.getWidth(prompt) / 2, y - 18, TEXT);
		}
		context.enableScissor(x, listTop, x + w, listBottom);
		int rowY = listTop - roleScroll;
		for (PainterChoiceOpenPayload.RoleChoice choice : filteredRoles) {
			if (rowY + ROW_H >= listTop && rowY <= listBottom) {
				boolean hovered = inside(mouseX, mouseY, x, rowY, w, ROW_H);
				if (hovered) context.fill(x, rowY, x + w, rowY + ROW_H, 0xAA2B1300);
				context.drawTextWithShadow(textRenderer,
					Text.literal(textRenderer.trimToWidth(choice.displayName(), w - 12)),
					x + 6, rowY + 6, 0xFF000000 | choice.color());
			}
			rowY += ROW_H;
		}
		context.disableScissor();
		if (filteredRoles.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.literal("No roles match."),
				x + 6, listTop + 6, MUTED);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 1 && mode == PainterChoiceOpenPayload.Mode.BODY && step == Step.ROLE) {
			step = Step.DEATH;
			selectedDeathReason = null;
			roleScroll = 0;
			layoutWidgets();
			return true;
		}
		if (button == 0 && step == Step.DEATH && clickDeath(mouseX, mouseY)) return true;
		if (button == 0 && step == Step.ROLE && clickRole(mouseX, mouseY)) return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean clickDeath(double mouseX, double mouseY) {
		int cols = deathColumns();
		int rows = deathRows(cols);
		int totalW = cols * SLOT + Math.max(0, cols - 1) * SLOT_GAP;
		int x = width / 2 - totalW / 2;
		int y = deathY(rows);
		for (int i = 0; i < deathChoices.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int cellX = x + col * (SLOT + SLOT_GAP);
			int cellY = y + row * (SLOT + 14);
			if (!inside(mouseX, mouseY, cellX, cellY, SLOT, SLOT)) continue;
			selectedDeathReason = deathChoices.get(i).deathReasonId();
			step = Step.ROLE;
			layoutWidgets();
			return true;
		}
		return false;
	}

	private boolean clickRole(double mouseX, double mouseY) {
		int w = roleBoxWidth();
		int x = roleBoxX(w);
		int listTop = roleBoxY() + 22;
		int rowY = listTop - roleScroll;
		for (PainterChoiceOpenPayload.RoleChoice choice : filteredRoles) {
			if (inside(mouseX, mouseY, x, rowY, w, ROW_H)) {
				submit(choice.roleId());
				return true;
			}
			rowY += ROW_H;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (step == Step.ROLE) {
			int visible = visibleRoleRows() * ROW_H;
			int max = Math.max(0, filteredRoles.size() * ROW_H - visible);
			roleScroll = MathHelper.clamp(roleScroll - (int) Math.round(verticalAmount * 24.0D), 0, max);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(null);
	}

	private void submit(Identifier roleId) {
		if (roleId == null || targetId == null || !ClientPlayNetworking.canSend(PainterChoiceSubmitPayload.ID)) {
			close();
			return;
		}
		ClientPlayNetworking.send(new PainterChoiceSubmitPayload(mode, targetId,
			mode == PainterChoiceOpenPayload.Mode.BODY ? selectedDeathReason : null, roleId));
		close();
	}

	private void refilter() {
		filteredRoles.clear();
		String lower = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
		for (PainterChoiceOpenPayload.RoleChoice choice : roleChoices) {
			if (choice == null) continue;
			if (lower.isEmpty()
					|| choice.displayName().toLowerCase(Locale.ROOT).contains(lower)
					|| choice.roleId().toString().toLowerCase(Locale.ROOT).contains(lower)) {
				filteredRoles.add(choice);
			}
		}
	}

	private void layoutWidgets() {
		if (searchField == null) return;
		int w = roleBoxWidth();
		int x = roleBoxX(w);
		int y = roleBoxY();
		searchField.visible = step == Step.ROLE;
		searchField.active = step == Step.ROLE;
		searchField.setDimensionsAndPosition(w, 20, x, y);
	}

	private void drawSlot(DrawContext context, int x, int y) {
		context.drawTexture(SLOT_TEXTURE, x, y, 32, 32, 0.0F, 0.0F, 32, 32, 32, 32);
	}

	private ItemStack itemStack(Identifier itemId) {
		return itemStack(itemId, "");
	}

	private ItemStack itemStack(Identifier itemId, String displayName) {
		Item item = itemId == null ? Items.PAPER : Registries.ITEM.get(itemId);
		if (item == Items.AIR) item = Items.PAPER;
		ItemStack stack = item.getDefaultStack();
		if (displayName != null && !displayName.isBlank()) {
			stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
		}
		return stack;
	}

	private void drawHoveredTooltip(DrawContext context, int mouseX, int mouseY) {
		if (step == Step.DEATH) {
			PainterChoiceOpenPayload.DeathChoice hovered = hoveredDeath(mouseX, mouseY);
			if (hovered != null) {
				context.drawItemTooltip(textRenderer, itemStack(hovered.itemId(), hovered.displayName()), mouseX, mouseY);
			}
			return;
		}
		PainterChoiceOpenPayload.RoleChoice hovered = hoveredRole(mouseX, mouseY);
		if (hovered != null) {
			context.drawTooltip(textRenderer, Text.literal(hovered.roleId().toString()), mouseX, mouseY);
		}
	}

	private PainterChoiceOpenPayload.DeathChoice hoveredDeath(double mouseX, double mouseY) {
		int cols = deathColumns();
		int rows = deathRows(cols);
		int totalW = cols * SLOT + Math.max(0, cols - 1) * SLOT_GAP;
		int x = width / 2 - totalW / 2;
		int y = deathY(rows);
		for (int i = 0; i < deathChoices.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int cellX = x + col * (SLOT + SLOT_GAP);
			int cellY = y + row * (SLOT + 14);
			if (inside(mouseX, mouseY, cellX, cellY, SLOT, SLOT)) return deathChoices.get(i);
		}
		return null;
	}

	private PainterChoiceOpenPayload.RoleChoice hoveredRole(double mouseX, double mouseY) {
		int w = roleBoxWidth();
		int x = roleBoxX(w);
		int listTop = roleBoxY() + 22;
		int rowY = listTop - roleScroll;
		for (PainterChoiceOpenPayload.RoleChoice choice : filteredRoles) {
			if (inside(mouseX, mouseY, x, rowY, w, ROW_H)) return choice;
			rowY += ROW_H;
		}
		return null;
	}

	private int deathColumns() {
		if (deathChoices.isEmpty()) return 1;
		int fit = Math.max(1, (width - 64 + SLOT_GAP) / (SLOT + SLOT_GAP));
		return Math.min(deathChoices.size(), fit);
	}

	private int deathRows(int cols) {
		return Math.max(1, (deathChoices.size() + Math.max(1, cols) - 1) / Math.max(1, cols));
	}

	private int deathY(int rows) {
		return MathHelper.clamp(height / 2 + 64, 56, Math.max(56, height - rows * (SLOT + 14) - 42));
	}

	private int roleBoxWidth() {
		return Math.min(300, Math.max(180, width - 80));
	}

	private int roleBoxX(int w) {
		return width / 2 - w / 2;
	}

	private int roleBoxY() {
		int boxHeight = 22 + visibleRoleRows() * ROW_H;
		return MathHelper.clamp((height - boxHeight) / 2, 48, Math.max(48, height - boxHeight - 48));
	}

	private int visibleRoleRows() {
		int available = Math.max(ROW_H, height - 130);
		return Math.max(1, Math.min(8, available / ROW_H));
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private enum Step {
		DEATH,
		ROLE
	}
}
