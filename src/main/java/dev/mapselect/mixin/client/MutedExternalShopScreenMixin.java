package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.modifier.PestControlManager;
import dev.mapselect.registry.MapSelectBlocks;
import dev.mapselect.role.GexpressRoleShop;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = LimitedInventoryScreen.class, remap = false, priority = 500)
public abstract class MutedExternalShopScreenMixin extends LimitedHandledScreen<PlayerScreenHandler> {
	@Shadow @Final public ClientPlayerEntity player;

	protected MutedExternalShopScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Inject(method = "init", at = @At("TAIL"), remap = false)
	private void gexpress$addGexpressItemsToExternalShop(CallbackInfo ci) {
		List<LimitedInventoryScreen.StoreItemWidget> storeWidgets = new ArrayList<>(children().stream()
			.filter(LimitedInventoryScreen.StoreItemWidget.class::isInstance)
			.map(LimitedInventoryScreen.StoreItemWidget.class::cast)
			.toList());
		boolean muted = GexpressRoleShop.hasMutedModifier(this.player);
		boolean pestControl = PestControlManager.has(this.player);
		boolean alreadyHasRatTrap = storeWidgets.stream()
			.anyMatch(widget -> widget.entry.stack().isOf(MapSelectBlocks.RAT_TRAP_ITEM));
		boolean alreadyHasNote = storeWidgets.stream()
			.anyMatch(widget -> widget.entry.stack().isOf(WatheItems.NOTE));
		boolean hasExistingShop = storeWidgets.stream().anyMatch(widget ->
			!widget.entry.stack().isOf(WatheItems.NOTE)
				&& !widget.entry.stack().isOf(MapSelectBlocks.RAT_TRAP_ITEM));

		// When another mod/role rendered a shop, replace any modifier-only row entries with
		// synthetic entries in that same row. This also handles unknown add-on shop roles.
		if (hasExistingShop && (muted || pestControl)) {
			List<LimitedInventoryScreen.StoreItemWidget> modifierWidgets = storeWidgets.stream()
				.filter(widget -> (muted && widget.entry.stack().isOf(WatheItems.NOTE))
					|| (pestControl && widget.entry.stack().isOf(MapSelectBlocks.RAT_TRAP_ITEM)))
				.toList();
			for (LimitedInventoryScreen.StoreItemWidget widget : modifierWidgets) remove(widget);
			storeWidgets.removeAll(modifierWidgets);
			if (muted) alreadyHasNote = false;
			if (pestControl) alreadyHasRatTrap = false;
		}

		boolean addNote = muted && !alreadyHasNote;
		boolean addRatTrap = pestControl && !alreadyHasRatTrap;
		if (!addNote && !addRatTrap) return;
		int renderedSize = storeWidgets.size();
		int totalSize = renderedSize + (addNote ? 1 : 0) + (addRatTrap ? 1 : 0);
		int apart = 36;
		int startX = this.width / 2 - totalSize * apart / 2 + 9;
		for (int i = 0; i < storeWidgets.size(); i++) {
			storeWidgets.get(i).setX(startX + apart * i);
		}
		int x = startX + apart * renderedSize;
		int y = storeWidgets.isEmpty()
			? ((((LimitedInventoryScreen) (Object) this).height - 32) / 2) - 46
			: storeWidgets.getFirst().getY();
		if (addNote) {
			addDrawableChild(new LimitedInventoryScreen.StoreItemWidget((LimitedInventoryScreen) (Object) this, x, y,
				GexpressRoleShop.mutedNoteEntry(), GexpressRoleShop.MUTED_EXTERNAL_NOTE_INDEX));
			x += apart;
		}
		if (addRatTrap) {
			addDrawableChild(new LimitedInventoryScreen.StoreItemWidget((LimitedInventoryScreen) (Object) this, x, y,
				GexpressRoleShop.ratTrapEntry(), GexpressRoleShop.PEST_CONTROL_EXTERNAL_TRAP_INDEX));
		}
	}
}
