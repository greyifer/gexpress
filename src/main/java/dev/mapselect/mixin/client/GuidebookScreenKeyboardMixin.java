package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.screen.GuidebookScreen;
import cat.rezelyn.watheextended.client.screen.guidebook.GuidebookEntry;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = GuidebookScreen.class, remap = false)
public abstract class GuidebookScreenKeyboardMixin {
	@Shadow private String selectedId;
	@Shadow private int currentPage;
	@Shadow private int leftScrollTarget;
	@Shadow private int leftPageH;
	@Shadow private int leftContentHeight;
	@Shadow @Final private static int PAGE_COUNT;

	@Shadow private List<GuidebookEntry> currentEntries() { throw new AssertionError(); }
	@Shadow private void selectEntry(GuidebookEntry entry) { throw new AssertionError(); }
	@Shadow private void changePage(int page) { throw new AssertionError(); }
	@Shadow private void scrollLeft(int amount) { throw new AssertionError(); }
	@Shadow private void scrollRight(int amount) { throw new AssertionError(); }

	@Inject(method = "method_25404", at = @At("HEAD"), cancellable = true, remap = false)
	private void gexpress$handleGuidebookKeyboard(int keyCode, int scanCode, int modifiers,
			CallbackInfoReturnable<Boolean> cir) {
		switch (keyCode) {
			case GLFW.GLFW_KEY_DOWN -> {
				if (gexpress$selectRelative(1)) cir.setReturnValue(true);
			}
			case GLFW.GLFW_KEY_UP -> {
				if (gexpress$selectRelative(-1)) cir.setReturnValue(true);
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				if (selectedId != null) {
					changePage(gexpress$clamp(currentPage + 1, 0, PAGE_COUNT - 1));
				} else {
					scrollLeft(10);
				}
				cir.setReturnValue(true);
			}
			case GLFW.GLFW_KEY_LEFT -> {
				if (selectedId != null) {
					changePage(gexpress$clamp(currentPage - 1, 0, PAGE_COUNT - 1));
				} else {
					scrollLeft(-10);
				}
				cir.setReturnValue(true);
			}
			case GLFW.GLFW_KEY_PAGE_DOWN -> {
				if (selectedId != null) scrollRight(leftPageH);
				else scrollLeft(leftPageH);
				cir.setReturnValue(true);
			}
			case GLFW.GLFW_KEY_PAGE_UP -> {
				if (selectedId != null) scrollRight(-leftPageH);
				else scrollLeft(-leftPageH);
				cir.setReturnValue(true);
			}
			default -> {
			}
		}
	}

	@Unique
	private boolean gexpress$selectRelative(int direction) {
		List<GuidebookEntry> entries = currentEntries();
		if (entries == null || entries.isEmpty()) return false;

		int current = gexpress$currentSelectableIndex(entries);
		int index = current;
		if (index < 0) {
			index = direction >= 0 ? -1 : entries.size();
		}

		for (int i = 0; i < entries.size(); i++) {
			index += direction;
			if (index < 0) index = entries.size() - 1;
			else if (index >= entries.size()) index = 0;
			GuidebookEntry entry = entries.get(index);
			if (!gexpress$isSelectable(entry)) continue;
			selectEntry(entry);
			gexpress$ensureEntryVisible(entries, index);
			return true;
		}
		return false;
	}

	@Unique
	private int gexpress$currentSelectableIndex(List<GuidebookEntry> entries) {
		if (selectedId == null) return -1;
		for (int i = 0; i < entries.size(); i++) {
			GuidebookEntry entry = entries.get(i);
			if (entry != null && selectedId.equals(entry.id())) return i;
		}
		return -1;
	}

	@Unique
	private boolean gexpress$isSelectable(GuidebookEntry entry) {
		return entry != null
			&& !entry.isHeader()
			&& entry.id() != null
			&& entry.descriptionKey() != null
			&& !entry.descriptionKey().isBlank();
	}

	@Unique
	private void gexpress$ensureEntryVisible(List<GuidebookEntry> entries, int index) {
		int y = 0;
		for (int i = 0; i < index && i < entries.size(); i++) {
			y += gexpress$rowHeight(entries.get(i));
		}
		int rowHeight = gexpress$rowHeight(entries.get(index));
		int maxScroll = Math.max(0, leftContentHeight - leftPageH);
		if (y < leftScrollTarget) {
			leftScrollTarget = gexpress$clamp(y, 0, maxScroll);
		} else if (y + rowHeight > leftScrollTarget + leftPageH) {
			leftScrollTarget = gexpress$clamp(y + rowHeight - leftPageH, 0, maxScroll);
		}
	}

	@Unique
	private int gexpress$rowHeight(GuidebookEntry entry) {
		if (entry == null || entry.text() == null || entry.text().getString().isEmpty()) return 6;
		return 15;
	}

	@Unique
	private int gexpress$clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
