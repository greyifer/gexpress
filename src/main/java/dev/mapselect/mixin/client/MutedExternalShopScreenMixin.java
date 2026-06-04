package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
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

@Mixin(value = LimitedInventoryScreen.class, remap = false)
public abstract class MutedExternalShopScreenMixin extends LimitedHandledScreen<PlayerScreenHandler> {
	@Shadow @Final public ClientPlayerEntity player;

	protected MutedExternalShopScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Inject(method = "init", at = @At("TAIL"), remap = false)
	private void gexpress$addMutedNoteToExternalShop(CallbackInfo ci) {
		if (!GexpressRoleShop.usesExternalMutedNoteWidget(this.player)) return;
		int externalSize = GexpressRoleShop.externalOverlayShopSize(this.player);
		if (externalSize <= 0) return;
		int apart = 36;
		int x = this.width / 2 - externalSize * apart / 2 + 9 + externalSize * apart;
		int shouldBeY = (((LimitedInventoryScreen) (Object) this).height - 32) / 2;
		int y = shouldBeY - 46;
		addDrawableChild(new LimitedInventoryScreen.StoreItemWidget((LimitedInventoryScreen) (Object) this, x, y,
			GexpressRoleShop.mutedNoteEntry(), GexpressRoleShop.MUTED_EXTERNAL_NOTE_INDEX));
	}
}
