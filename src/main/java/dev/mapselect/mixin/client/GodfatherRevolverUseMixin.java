package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.item.RevolverItem;
import dev.mapselect.client.ClientMafiaState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RevolverItem.class, remap = false)
public abstract class GodfatherRevolverUseMixin {
	@Dynamic("RevolverItem is compiled against intermediary Item#use in the Wathe jar.")
	@Inject(method = "method_7836", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockEmptyGodfatherRevolver(@NotNull World world, @NotNull PlayerEntity user,
			Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		if (world.isClient && ClientMafiaState.shouldBlockLocalGodfatherShot()) {
			cir.setReturnValue(TypedActionResult.fail(user.getStackInHand(hand)));
		}
	}
}
