package dev.mapselect.mixin.client;

import dev.mapselect.MapSelect;
import dev.mapselect.client.GexpressCreativeSectionScreen;
import dev.mapselect.registry.MapSelectItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(CreativeInventoryScreen.class)
public abstract class GexpressCreativeInventorySectionsMixin
		extends AbstractInventoryScreen<CreativeInventoryScreen.CreativeScreenHandler>
		implements GexpressCreativeSectionScreen {
	private static final Identifier GEXPRESS_GROUP_ID = Identifier.of(MapSelect.MOD_ID, "special_blocks");
	private static final int GRID_LEFT = 9;
	private static final int GRID_TOP = 18;
	private static final int GRID_WIDTH = 160;
	private static final int GRID_HEIGHT = 90;
	private static final int ROW_HEIGHT = 18;
	private static final int COLUMNS = 9;
	private static final int CREATIVE_GRID_SLOT_COUNT = 45;
	private static final int SECTION_NONE = 0;
	private static final int SECTION_DECORATION_BLOCKS = 1;
	private static final int SECTION_ENVIRONMENTAL_BLOCKS = 2;
	private static final int SECTION_BUILDING_BLOCKS = 3;
	private static final int SECTION_ITEMS = 4;
	private static final int SECTION_PADDING = 5;
	private static final float SECTION_ANIMATION_SECONDS = 0.22F;
	private static final float ANIMATION_EPSILON = 0.015F;
	private static final Set<Integer> COLLAPSED_SECTIONS = new HashSet<>();

	private final float[] gexpress$sectionOpenProgress = {1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F};
	private List<ItemStack> gexpress$fullDisplayStacks = List.of();
	private boolean gexpress$animatingSections;
	private float gexpress$scrollPixels;
	private long gexpress$lastAnimationMs;

	@Shadow
	private static ItemGroup selectedTab;

	@Shadow
	private float scrollPosition;

	@Shadow
	private void refreshSelectedTab(Collection<ItemStack> displayStacks) {
		throw new AssertionError();
	}

	private GexpressCreativeInventorySectionsMixin(CreativeInventoryScreen.CreativeScreenHandler handler,
			PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Inject(method = "refreshSelectedTab", at = @At("TAIL"))
	private void gexpress$filterCollapsedSections(Collection<ItemStack> displayStacks, CallbackInfo ci) {
		if (gexpress$isGexpressGroup()) {
			this.gexpress$fullDisplayStacks = List.copyOf(displayStacks);
		}
		gexpress$filterCollapsedSections();
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void gexpress$animateSections(DrawContext context, int mouseX, int mouseY, float delta,
			CallbackInfo ci) {
		if (!gexpress$isGexpressGroup() || !this.gexpress$animatingSections) return;
		long now = Util.getMeasuringTimeMs();
		long lastAnimationMs = this.gexpress$lastAnimationMs == 0L ? now : this.gexpress$lastAnimationMs;
		this.gexpress$lastAnimationMs = now;
		float elapsedSeconds = MathHelper.clamp((now - lastAnimationMs) / 1000.0F, 0.0F, 0.05F);
		float step = elapsedSeconds / SECTION_ANIMATION_SECONDS;
		boolean anyRemaining = false;
		for (int section = SECTION_DECORATION_BLOCKS; section <= SECTION_ITEMS; section++) {
			float target = gexpress$isCollapsed(section) ? 0.0F : 1.0F;
			float current = this.gexpress$sectionOpenProgress[section];
			float next = MathHelper.clamp(current + Math.signum(target - current) * step, 0.0F, 1.0F);
			if (Math.abs(next - target) <= ANIMATION_EPSILON) {
				next = target;
			}
			this.gexpress$sectionOpenProgress[section] = next;
			if (Math.abs(next - target) > ANIMATION_EPSILON) {
				anyRemaining = true;
			}
		}
		if (!anyRemaining) {
			this.gexpress$animatingSections = false;
			this.gexpress$lastAnimationMs = 0L;
			if (selectedTab != null) {
				this.refreshSelectedTab(selectedTab.getDisplayStacks());
			}
			gexpress$syncVanillaScrollToAnimatedView();
		}
	}

	@Inject(method = "drawForeground", at = @At("TAIL"))
	private void gexpress$drawSectionRows(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
		if (!gexpress$isGexpressGroup()) return;
		if (gexpress$isSectionAnimationActive()) {
			gexpress$drawAnimatedSections(context, mouseX, mouseY);
			return;
		}
		Set<Integer> drawnRows = new HashSet<>();
		int localMouseX = mouseX - this.x;
		int localMouseY = mouseY - this.y;
		for (Slot slot : this.handler.slots) {
			ItemStack stack = slot.getStack();
			int section = gexpress$sectionFor(stack);
			if (section == SECTION_NONE || section == SECTION_PADDING || !drawnRows.add(slot.y)) continue;
			int y = slot.y - 1;
			boolean hovered = localMouseX >= GRID_LEFT && localMouseX < GRID_LEFT + GRID_WIDTH
				&& localMouseY >= y && localMouseY < y + 18;
			gexpress$drawSection(context, slot.y - 1, gexpress$sectionLabel(section),
				gexpress$sectionFill(section), gexpress$sectionBorder(section), section, hovered);
		}
	}

	@Inject(method = "onMouseClick", at = @At("HEAD"), cancellable = true)
	private void gexpress$cancelSectionClicks(Slot slot, int slotId, int button, SlotActionType actionType,
			CallbackInfo ci) {
		if (gexpress$isSectionAnimationActive() && slot != null && slot.id < CREATIVE_GRID_SLOT_COUNT) {
			ci.cancel();
			return;
		}
		int section = slot == null ? SECTION_NONE : gexpress$sectionFor(slot.getStack());
		if (section == SECTION_PADDING) {
			ci.cancel();
			return;
		}
		if (gexpress$isCollapsibleSection(section)) {
			if (button == 0 && actionType == SlotActionType.PICKUP) {
				gexpress$toggleSection(section);
			}
			ci.cancel();
		}
	}

	@Inject(method = "getTooltipFromItem", at = @At("HEAD"), cancellable = true)
	private void gexpress$hideSectionTooltips(ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
		if (gexpress$isSectionAnimationActive() || gexpress$sectionFor(stack) != SECTION_NONE) {
			cir.setReturnValue(List.of());
		}
	}

	@Override
	public boolean gexpress$isSectionAnimationActive() {
		return gexpress$isGexpressGroup() && this.gexpress$animatingSections;
	}

	@Override
	public boolean gexpress$shouldSkipCreativeSlot(Slot slot) {
		return gexpress$isSectionAnimationActive() && slot != null && slot.id < CREATIVE_GRID_SLOT_COUNT;
	}

	private boolean gexpress$isGexpressGroup() {
		return selectedTab != null && GEXPRESS_GROUP_ID.equals(Registries.ITEM_GROUP.getId(selectedTab));
	}

	private void gexpress$toggleSection(int section) {
		List<ItemStack> stacks = gexpress$getFullDisplayStacks();
		float overflowBefore = Math.max(0.0F, gexpress$totalVisualHeight(stacks) - GRID_HEIGHT);
		this.gexpress$scrollPixels = this.scrollPosition * overflowBefore;
		if (!COLLAPSED_SECTIONS.remove(section)) {
			COLLAPSED_SECTIONS.add(section);
		}
		this.gexpress$animatingSections = true;
		this.gexpress$lastAnimationMs = Util.getMeasuringTimeMs();
	}

	private void gexpress$filterCollapsedSections() {
		if (!gexpress$isGexpressGroup() || this.gexpress$animatingSections || COLLAPSED_SECTIONS.isEmpty()) return;
		List<ItemStack> sourceStacks = new ArrayList<>(this.handler.itemList);
		List<ItemStack> filteredStacks = new ArrayList<>(this.handler.itemList.size());
		int activeSection = SECTION_NONE;
		boolean activeCollapsed = false;

		for (ItemStack stack : sourceStacks) {
			int section = gexpress$sectionFor(stack);
			if (gexpress$isCollapsibleSection(section)) {
				activeSection = section;
				activeCollapsed = gexpress$isCollapsed(activeSection);
				filteredStacks.add(stack);
				continue;
			}
			if (activeSection == SECTION_NONE || !activeCollapsed) {
				filteredStacks.add(stack);
			}
		}

		if (filteredStacks.size() == sourceStacks.size()) return;
		this.handler.itemList.clear();
		this.handler.itemList.addAll(filteredStacks);
		this.handler.scrollItems(this.scrollPosition);
	}

	private void gexpress$syncVanillaScrollToAnimatedView() {
		float overflow = Math.max(0.0F, (float) (((this.handler.itemList.size() + COLUMNS - 1) / COLUMNS) * ROW_HEIGHT - GRID_HEIGHT));
		this.scrollPosition = overflow <= 0.0F ? 0.0F
			: MathHelper.clamp(this.gexpress$scrollPixels / overflow, 0.0F, 1.0F);
		this.handler.scrollItems(this.scrollPosition);
	}

	private boolean gexpress$isSectionAnimating(int section) {
		float target = gexpress$isCollapsed(section) ? 0.0F : 1.0F;
		return Math.abs(this.gexpress$sectionOpenProgress[section] - target) > ANIMATION_EPSILON;
	}

	private void gexpress$drawAnimatedSections(DrawContext context, int mouseX, int mouseY) {
		List<ItemStack> stacks = gexpress$getFullDisplayStacks();
		int localMouseX = mouseX - this.x;
		int localMouseY = mouseY - this.y;

		context.enableScissor(this.x + GRID_LEFT, this.y + GRID_TOP, this.x + GRID_LEFT + GRID_WIDTH,
			this.y + GRID_TOP + GRID_HEIGHT);
		float rowY = GRID_TOP - this.gexpress$scrollPixels;
		int row = 0;
		int rowCount = gexpress$rowCount(stacks);

		while (row < rowCount) {
			int section = gexpress$rowSection(stacks, row);
			if (!gexpress$isCollapsibleSection(section)) {
				gexpress$drawItemRow(context, stacks, row, rowY);
				rowY += ROW_HEIGHT;
				row++;
				continue;
			}

			boolean hovered = localMouseX >= GRID_LEFT && localMouseX < GRID_LEFT + GRID_WIDTH
				&& localMouseY >= rowY && localMouseY < rowY + ROW_HEIGHT;
			gexpress$drawSection(context, Math.round(rowY - 1.0F), gexpress$sectionLabel(section),
				gexpress$sectionFill(section), gexpress$sectionBorder(section), section, hovered);
			rowY += ROW_HEIGHT;
			row++;

			int contentStartRow = row;
			while (row < rowCount && !gexpress$isCollapsibleSection(gexpress$rowSection(stacks, row))) {
				row++;
			}
			int contentRows = row - contentStartRow;
			float contentHeight = contentRows * ROW_HEIGHT;
			float visibleHeight = contentHeight * gexpress$sectionVisualProgress(section);
			if (visibleHeight > 0.5F) {
				int clipTop = Math.max(this.y + GRID_TOP, this.y + Math.round(rowY));
				int clipBottom = Math.min(this.y + GRID_TOP + GRID_HEIGHT, this.y + Math.round(rowY + visibleHeight));
				if (clipBottom > clipTop) {
					context.enableScissor(this.x + GRID_LEFT, clipTop, this.x + GRID_LEFT + GRID_WIDTH, clipBottom);
					int firstVisibleRow = Math.max(0,
						(int) Math.floor((GRID_TOP - rowY) / ROW_HEIGHT) - 1);
					int lastVisibleRow = Math.min(contentRows,
						(int) Math.ceil((GRID_TOP + GRID_HEIGHT - rowY) / ROW_HEIGHT) + 1);
					for (int contentRow = firstVisibleRow; contentRow < lastVisibleRow; contentRow++) {
						gexpress$drawItemRow(context, stacks, contentStartRow + contentRow,
							rowY + contentRow * ROW_HEIGHT);
					}
					context.disableScissor();
				}
			}
			rowY += visibleHeight;
		}
		context.disableScissor();
	}

	private void gexpress$drawItemRow(DrawContext context, List<ItemStack> stacks, int row, float y) {
		if (y > GRID_TOP + GRID_HEIGHT || y + ROW_HEIGHT < GRID_TOP) return;
		int itemY = Math.round(y);
		for (int column = 0; column < COLUMNS; column++) {
			ItemStack stack = gexpress$rowStack(stacks, row, column);
			if (stack.isEmpty() || gexpress$sectionFor(stack) != SECTION_NONE) continue;
			int itemX = GRID_LEFT + column * ROW_HEIGHT;
			context.drawItem(stack, itemX, itemY);
			context.drawItemInSlot(this.textRenderer, stack, itemX, itemY);
		}
	}

	private List<ItemStack> gexpress$getFullDisplayStacks() {
		if (this.gexpress$fullDisplayStacks.isEmpty() && selectedTab != null) {
			return List.copyOf(selectedTab.getDisplayStacks());
		}
		return this.gexpress$fullDisplayStacks;
	}

	private float gexpress$totalVisualHeight(List<ItemStack> stacks) {
		float height = 0.0F;
		int row = 0;
		int rowCount = gexpress$rowCount(stacks);
		while (row < rowCount) {
			int section = gexpress$rowSection(stacks, row);
			if (!gexpress$isCollapsibleSection(section)) {
				height += ROW_HEIGHT;
				row++;
				continue;
			}
			height += ROW_HEIGHT;
			row++;
			int contentStartRow = row;
			while (row < rowCount && !gexpress$isCollapsibleSection(gexpress$rowSection(stacks, row))) {
				row++;
			}
			height += (row - contentStartRow) * ROW_HEIGHT * gexpress$sectionVisualProgress(section);
		}
		return height;
	}

	private static int gexpress$rowCount(List<ItemStack> stacks) {
		return (stacks.size() + COLUMNS - 1) / COLUMNS;
	}

	private static ItemStack gexpress$rowStack(List<ItemStack> stacks, int row, int column) {
		int index = row * COLUMNS + column;
		return index >= 0 && index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
	}

	private static int gexpress$rowSection(List<ItemStack> stacks, int row) {
		for (int column = 0; column < COLUMNS; column++) {
			int section = gexpress$sectionFor(gexpress$rowStack(stacks, row, column));
			if (section != SECTION_NONE) return section;
		}
		return SECTION_NONE;
	}

	private static int gexpress$sectionFor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return SECTION_NONE;
		if (!stack.isOf(MapSelectItems.CREATIVE_DECORATION_BLOCKS_HEADER)
				&& !stack.isOf(MapSelectItems.CREATIVE_ENVIRONMENTAL_BLOCKS_HEADER)
				&& !stack.isOf(MapSelectItems.CREATIVE_BUILDING_BLOCKS_HEADER)
				&& !stack.isOf(MapSelectItems.CREATIVE_ITEMS_HEADER)) {
			return SECTION_NONE;
		}
		Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
		String marker = name == null ? "" : name.getString();
		if (marker.startsWith("gexpress_section_decoration_blocks")) return SECTION_DECORATION_BLOCKS;
		if (marker.startsWith("gexpress_section_environmental_blocks")) return SECTION_ENVIRONMENTAL_BLOCKS;
		if (marker.startsWith("gexpress_section_building_blocks")) return SECTION_BUILDING_BLOCKS;
		if (marker.startsWith("gexpress_section_items")) return SECTION_ITEMS;
		if (marker.startsWith("gexpress_section_padding")) return SECTION_PADDING;
		return SECTION_NONE;
	}

	private static boolean gexpress$isCollapsibleSection(int section) {
		return section >= SECTION_DECORATION_BLOCKS && section <= SECTION_ITEMS;
	}

	private static boolean gexpress$isCollapsed(int section) {
		return COLLAPSED_SECTIONS.contains(section);
	}

	private static String gexpress$sectionLabel(int section) {
		return switch (section) {
			case SECTION_DECORATION_BLOCKS -> "Decoration Blocks";
			case SECTION_ENVIRONMENTAL_BLOCKS -> "Environmental Blocks";
			case SECTION_BUILDING_BLOCKS -> "Building Blocks";
			case SECTION_ITEMS -> "Items";
			default -> "";
		};
	}

	private static int gexpress$sectionFill(int section) {
		return switch (section) {
			case SECTION_DECORATION_BLOCKS -> 0xFF5E4B74;
			case SECTION_ENVIRONMENTAL_BLOCKS -> 0xFF4C6D54;
			case SECTION_BUILDING_BLOCKS -> 0xFF536778;
			case SECTION_ITEMS -> 0xFF3F5E7F;
			default -> 0xFF333333;
		};
	}

	private static int gexpress$sectionBorder(int section) {
		return switch (section) {
			case SECTION_DECORATION_BLOCKS -> 0xFFE3C7FF;
			case SECTION_ENVIRONMENTAL_BLOCKS -> 0xFFCFE8B8;
			case SECTION_BUILDING_BLOCKS -> 0xFFC7DAEF;
			case SECTION_ITEMS -> 0xFFC9E0FF;
			default -> 0xFFFFFFFF;
		};
	}

	private void gexpress$drawSection(DrawContext context, int y, String label, int fill, int border, int section,
			boolean hovered) {
		int x = GRID_LEFT;
		int right = x + GRID_WIDTH;
		int bottom = y + 18;
		context.fill(x, y, right, bottom, fill);
		if (hovered) {
			context.fill(x, y, right, bottom, 0x22FFFFFF);
		}
		context.fill(x, y, right, y + 1, border);
		context.fill(x, bottom - 1, right, bottom, border);
		int indicatorX = right - 13;
		int indicatorY = y + 9;
		int labelWidth = indicatorX - x - 12;
		context.drawText(this.textRenderer, this.textRenderer.trimToWidth(label, labelWidth), x + 7, y + 5,
			0xFFFFFFFF, true);
		context.fill(indicatorX, indicatorY - 1, indicatorX + 7, indicatorY + 1, 0xFFFFFFFF);
		int verticalHalf = Math.round((1.0F - gexpress$sectionVisualProgress(section)) * 4.0F);
		if (verticalHalf > 0) {
			context.fill(indicatorX + 3, indicatorY - verticalHalf, indicatorX + 5, indicatorY + verticalHalf,
				0xFFFFFFFF);
		}
	}

	private float gexpress$sectionVisualProgress(int section) {
		float progress = MathHelper.clamp(this.gexpress$sectionOpenProgress[section], 0.0F, 1.0F);
		return progress * progress * (3.0F - 2.0F * progress);
	}

}
