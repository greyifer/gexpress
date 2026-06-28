package dev.mapselect.mixin;

import dev.mapselect.game.ItemPickupPolicyManager;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemPickupPolicyMixin {
	@Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
	private void gexpress$limitRoundItemPickup(PlayerEntity player, CallbackInfo ci) {
		ItemEntity item = (ItemEntity) (Object) this;
		if (!ItemPickupPolicyManager.canPickup(player, item.getStack())) ci.cancel();
	}
}
