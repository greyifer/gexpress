package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.mapselect.client.role.copycat.CopycatInventoryInput;
import dev.mapselect.client.role.doomsayer.DoomsayerInventoryInput;
import dev.mapselect.client.role.inventory.RoleSelectionInventoryInput;
import dev.mapselect.client.role.pelican.PelicanInventoryInput;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One click entry point for role overlays, avoiding competing subclass mouseClicked overrides. */
@Mixin(value = LimitedHandledScreen.class, remap = false)
public abstract class LimitedInventoryRoleInputMixin {
	@Dynamic("Wathe uses an intermediary Minecraft method name in production jars.")
	@Inject(
		method = {"mouseClicked(DDI)Z", "method_25402(DDI)Z"},
		at = @At("HEAD"),
		cancellable = true,
		remap = false
	)
	private void gexpress$handleRoleOverlayClick(double mouseX, double mouseY, int button,
			CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof dev.mapselect.client.role.counterfeiter.CounterfeiterInventoryInput forge
                && forge.gexpress$handleCounterfeiterMouse(mouseX, mouseY, button)) {
            cir.setReturnValue(true); return;
        }
        if ((Object) this instanceof RoleSelectionInventoryInput selection
				&& selection.gexpress$handleRoleSelectionMouse(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
			return;
		}
		if ((Object) this instanceof DoomsayerInventoryInput doomsayer
				&& doomsayer.gexpress$handleDoomsayerMouse(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
			return;
		}
		if ((Object) this instanceof PelicanInventoryInput pelican
				&& pelican.gexpress$handlePelicanMouse(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
			return;
		}
		if ((Object) this instanceof CopycatInventoryInput copycat
				&& copycat.gexpress$handleCopycatMouse(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
		}
	}
}
