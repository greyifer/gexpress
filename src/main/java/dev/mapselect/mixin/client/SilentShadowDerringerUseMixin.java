package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.item.DerringerItem;
import dev.mapselect.role.silent.SilentShadowComponent;
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

@Mixin(value = DerringerItem.class, remap = false)
public abstract class SilentShadowDerringerUseMixin {
	@Dynamic("DerringerItem is compiled against intermediary Item#use in the Wathe jar.")
	@Inject(method = "method_7836", at = @At("HEAD"), cancellable = true, remap = false)
	private void gexpress$blockShadowMarchDerringer(@NotNull World world, @NotNull PlayerEntity user,
			Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		if (SilentShadowComponent.isShadowed(user)) {
			cir.setReturnValue(TypedActionResult.fail(user.getStackInHand(hand)));
		}
	}
}
