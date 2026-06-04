package dev.mapselect.mixin.client;

import dev.mapselect.MapSelect;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(CreativeInventoryScreen.class)
public abstract class GexpressCreativeInventorySectionsMixin
		extends AbstractInventoryScreen<CreativeInventoryScreen.CreativeScreenHandler> {
	private static final Identifier GEXPRESS_GROUP_ID = Identifier.of(MapSelect.MOD_ID, "special_blocks");
	private static final int GRID_LEFT = 8;
	private static final int GRID_WIDTH = 162;
	private static final int SECTION_NONE = 0;
	private static final int SECTION_DECORATION_BLOCKS = 1;
	private static final int SECTION_ENVIRONMENTAL_BLOCKS = 2;
	private static final int SECTION_BUILDING_BLOCKS = 3;
	private static final int SECTION_ITEMS = 4;
	private static final int SECTION_PADDING = 5;

	@Shadow
	private static ItemGroup selectedTab;

	private GexpressCreativeInventorySectionsMixin(CreativeInventoryScreen.CreativeScreenHandler handler,
			PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Inject(method = "drawForeground", at = @At("TAIL"))
	private void gexpress$drawSectionRows(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
		if (!gexpress$isGexpressGroup()) return;
		Set<Integer> drawnRows = new HashSet<>();
		for (Slot slot : this.handler.slots) {
			ItemStack stack = slot.getStack();
			int section = gexpress$sectionFor(stack);
			if (section == SECTION_NONE || section == SECTION_PADDING || !drawnRows.add(slot.y)) continue;
			gexpress$drawSection(context, slot.y - 1, gexpress$sectionLabel(section),
				gexpress$sectionFill(section), gexpress$sectionBorder(section));
		}
	}

	@Inject(method = "onMouseClick", at = @At("HEAD"), cancellable = true)
	private void gexpress$cancelSectionClicks(Slot slot, int slotId, int button, SlotActionType actionType,
			CallbackInfo ci) {
		if (slot != null && gexpress$sectionFor(slot.getStack()) != SECTION_NONE) {
			ci.cancel();
		}
	}

	@Inject(method = "getTooltipFromItem", at = @At("HEAD"), cancellable = true)
	private void gexpress$hideSectionTooltips(ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
		if (gexpress$sectionFor(stack) != SECTION_NONE) {
			cir.setReturnValue(List.of());
		}
	}

	private boolean gexpress$isGexpressGroup() {
		return selectedTab != null && GEXPRESS_GROUP_ID.equals(Registries.ITEM_GROUP.getId(selectedTab));
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

	private void gexpress$drawSection(DrawContext context, int y, String label, int fill, int border) {
		int x = GRID_LEFT;
		int right = x + GRID_WIDTH;
		int bottom = y + 18;
		context.fill(x, y, right, bottom, fill);
		context.fill(x, y, right, y + 1, border);
		context.fill(x, bottom - 1, right, bottom, border);
		context.drawText(this.textRenderer, label, x + 7, y + 5, 0xFFFFFFFF, true);
	}

}
