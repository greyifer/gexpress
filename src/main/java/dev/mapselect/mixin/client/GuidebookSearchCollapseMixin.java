package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.screen.GuidebookScreen;
import cat.rezelyn.watheextended.client.screen.guidebook.GuidebookEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Mixin(value = GuidebookScreen.class, remap = false, priority = 900)
public abstract class GuidebookSearchCollapseMixin extends Screen {
	@Shadow private int leftPageX;
	@Shadow private int leftPageY;
	@Shadow private int leftPageW;
	@Shadow private int leftPageH;
	@Shadow private int leftScrollTarget;
	@Shadow private float leftScrollSmooth;
	@Shadow private int leftContentHeight;
	@Shadow private List<GuidebookEntry> currentEntries() { throw new AssertionError(); }
	@Shadow private void recalcLeftHeight() { throw new AssertionError(); }

	@Unique private final Set<String> gexpress$collapsedSections = new HashSet<>();
	@Unique private String gexpress$search = "";
	@Unique private boolean gexpress$searchFocused;

	protected GuidebookSearchCollapseMixin(Text title) {
		super(title);
	}

	@Inject(method = "currentEntries", at = @At("RETURN"), cancellable = true)
	private void gexpress$filterEntries(CallbackInfoReturnable<List<GuidebookEntry>> cir) {
		List<GuidebookEntry> entries = cir.getReturnValue();
		if (entries == null || entries.isEmpty()) return;
		String query = gexpress$search.trim().toLowerCase(Locale.ROOT);
		boolean searching = !query.isEmpty();
		List<GuidebookEntry> filtered = new ArrayList<>(entries.size());
		boolean headerCollapsed = false;
		boolean subheaderCollapsed = false;

		for (GuidebookEntry entry : entries) {
			if (entry == null) continue;
			if (entry.isHeader()) {
				headerCollapsed = !searching && gexpress$collapsedSections.contains(gexpress$collapseKey(entry));
				subheaderCollapsed = false;
				filtered.add(entry);
				continue;
			}
			if (gexpress$isSubheader(entry)) {
				if (!headerCollapsed) filtered.add(entry);
				subheaderCollapsed = !searching && gexpress$collapsedSections.contains(gexpress$collapseKey(entry));
				continue;
			}
			if (headerCollapsed || subheaderCollapsed) continue;
			if (searching && !gexpress$matches(entry, query)) continue;
			filtered.add(entry);
		}
		cir.setReturnValue(filtered);
	}

	@Inject(method = "method_25394", at = @At("TAIL"))
	private void gexpress$renderSearch(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.textRenderer == null) return;
		TextRenderer renderer = client.textRenderer;
		gexpress$renderHeaderHover(context, mouseX, mouseY);
		int x = gexpress$searchX();
		int y = gexpress$searchY();
		int w = gexpress$searchW();
		String value = gexpress$search.isBlank() ? "Search roles or modifiers" : gexpress$search;
		int color = gexpress$search.isBlank() ? 0xFF6F6655 : 0xFF332819;
		context.drawText(renderer, renderer.trimToWidth(value, w - 12), x + 6, y + 4, color, false);
	}

	@Inject(method = "method_25402", at = @At("HEAD"), cancellable = true)
	private void gexpress$mouseClicked(double mouseX, double mouseY, int button,
			CallbackInfoReturnable<Boolean> cir) {
		if (button != 0) return;
		if (gexpress$isInsideSearch(mouseX, mouseY)) {
			gexpress$searchFocused = true;
			cir.setReturnValue(true);
			return;
		}
		gexpress$searchFocused = false;
		GuidebookEntry row = gexpress$rowAt(mouseX, mouseY);
		if (row != null && (row.isHeader() || gexpress$isSubheader(row))) {
			String key = gexpress$collapseKey(row);
			if (!gexpress$collapsedSections.remove(key)) gexpress$collapsedSections.add(key);
			gexpress$refreshFilteredEntries();
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "method_25404", at = @At("HEAD"), cancellable = true)
	private void gexpress$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_F) {
			gexpress$searchFocused = true;
			cir.setReturnValue(true);
			return;
		}
		if (!gexpress$searchFocused) return;
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
			gexpress$searchFocused = false;
			cir.setReturnValue(true);
			return;
		}
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !gexpress$search.isEmpty()) {
			gexpress$search = gexpress$search.substring(0, gexpress$search.length() - 1);
			gexpress$refreshFilteredEntries();
			cir.setReturnValue(true);
			return;
		}
		if (keyCode == GLFW.GLFW_KEY_DELETE && !gexpress$search.isEmpty()) {
			gexpress$search = "";
			gexpress$refreshFilteredEntries();
			cir.setReturnValue(true);
		}
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (!gexpress$searchFocused) return super.charTyped(chr, modifiers);
		if (!Character.isISOControl(chr)) {
			gexpress$search += chr;
			gexpress$refreshFilteredEntries();
		}
		return true;
	}

	@Unique
	private GuidebookEntry gexpress$rowAt(double mouseX, double mouseY) {
		if (mouseX < leftPageX || mouseX >= leftPageX + leftPageW || mouseY < leftPageY || mouseY >= leftPageY + leftPageH) {
			return null;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer renderer = client == null ? null : client.textRenderer;
		if (renderer == null) return null;
		int y = gexpress$firstRowY();
		for (GuidebookEntry entry : currentEntries()) {
			int rowHeight = gexpress$rowHeight(renderer, entry);
			if (mouseY >= y && mouseY < y + rowHeight) return entry;
			y += rowHeight;
		}
		return null;
	}

	@Unique
	private void gexpress$renderHeaderHover(DrawContext context, int mouseX, int mouseY) {
		GuidebookEntry row = gexpress$rowAt(mouseX, mouseY);
		if (row == null || (!row.isHeader() && !gexpress$isSubheader(row))) return;
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer renderer = client == null ? null : client.textRenderer;
		if (renderer == null) return;
		int y = gexpress$rowY(renderer, row);
		if (y < leftPageY || y >= leftPageY + leftPageH) return;
		int height = gexpress$rowHeight(renderer, row);
		context.fill(leftPageX, y, leftPageX + leftPageW,
			Math.min(leftPageY + leftPageH, y + height), 0x1A000000);
		gexpress$redrawHoveredText(context, renderer, row, y);
	}

	@Unique
	private int gexpress$rowY(TextRenderer renderer, GuidebookEntry target) {
		int y = gexpress$firstRowY();
		for (GuidebookEntry entry : currentEntries()) {
			if (entry == target) return y;
			y += gexpress$rowHeight(renderer, entry);
		}
		return y;
	}

	@Unique
	private void gexpress$redrawHoveredText(DrawContext context, TextRenderer renderer, GuidebookEntry row, int y) {
		int color = gexpress$opaque(row.color());
		if (row.isHeader()) {
			context.drawTextWithShadow(renderer, row.text(), leftPageX + 6, y, color);
			return;
		}
		int textY = y + 2;
		for (OrderedText line : renderer.wrapLines(row.text(), leftPageW - 12)) {
			context.drawText(renderer, line, leftPageX + 6, textY, color, false);
			textY += 13;
		}
	}

	@Unique
	private void gexpress$refreshFilteredEntries() {
		recalcLeftHeight();
		leftScrollTarget = Math.max(0, Math.min(leftScrollTarget, Math.max(0, leftContentHeight - leftPageH)));
	}

	@Unique
	private boolean gexpress$matches(GuidebookEntry entry, String query) {
		return gexpress$contains(entry.text(), query)
			|| gexpress$contains(entry.displayTitle(), query)
			|| gexpress$contains(entry.id(), query)
			|| gexpress$contains(entry.descriptionKey(), query);
	}

	@Unique
	private boolean gexpress$contains(Text text, String query) {
		return text != null && gexpress$contains(text.getString(), query);
	}

	@Unique
	private boolean gexpress$contains(String value, String query) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(query);
	}

	@Unique
	private boolean gexpress$isSubheader(GuidebookEntry entry) {
		return entry != null && !entry.isHeader() && entry.id() == null && entry.descriptionKey() == null
			&& entry.text() != null && !entry.text().getString().isEmpty();
	}

	@Unique
	private String gexpress$collapseKey(GuidebookEntry entry) {
		String prefix = entry.isHeader() ? "header:" : "subheader:";
		String text = entry.text() == null ? "" : entry.text().getString().trim();
		return prefix + text;
	}

	@Unique
	private int gexpress$rowHeight(TextRenderer renderer, GuidebookEntry entry) {
		if (entry == null || entry.text() == null || entry.text().getString().isEmpty()) return 6;
		if (entry.isHeader()) return 15;
		return Math.max(13, renderer.wrapLines(entry.text(), leftPageW - 12).size() * 13);
	}

	@Unique private int gexpress$opaque(int color) { return 0xFF000000 | (color & 0x00FFFFFF); }
	@Unique private int gexpress$firstRowY() { return leftPageY + 6 - (int) leftScrollSmooth; }
	@Unique private int gexpress$searchX() { return leftPageX + 4; }
	@Unique private int gexpress$searchY() { return leftPageY + leftPageH + 3; }
	@Unique private int gexpress$searchW() { return Math.max(24, leftPageW - 8); }
	@Unique private int gexpress$searchH() { return 14; }

	@Unique
	private boolean gexpress$isInsideSearch(double mouseX, double mouseY) {
		int x = gexpress$searchX();
		int y = gexpress$searchY();
		int w = gexpress$searchW();
		int h = gexpress$searchH();
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}
}
