package dev.mapselect.mixin;

import dev.mapselect.role.mafia.MafiaManager;
import dev.mapselect.role.bodyguard.BodyguardManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(PlayerInventory.class)
public abstract class MafiaWeaponInventoryMixin {
	@Shadow @Final public PlayerEntity player;

	@Inject(method = "remove", at = @At("HEAD"), cancellable = true)
	private void gexpress$keepMafiaRevolver(Predicate<ItemStack> shouldRemove, int maxCount,
			Inventory craftingInventory, CallbackInfoReturnable<Integer> cir) {
		if (MafiaManager.shouldBlockWeaponRemoval(player, shouldRemove, maxCount)
				|| BodyguardManager.shouldBlockWeaponRemoval(player, shouldRemove, maxCount)) {
			cir.setReturnValue(0);
		}
	}
}
