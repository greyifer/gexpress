package dev.mapselect.mixin;

import dev.mapselect.role.juggernaut.JuggernautManager;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class JuggernautDropMixin {
	@Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockJuggernautLoadoutDrop(ItemStack stack, boolean throwRandomly,
			boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (JuggernautManager.shouldBlockLoadoutDrop(player, stack)) {
			cir.setReturnValue(null);
		}
	}
}
