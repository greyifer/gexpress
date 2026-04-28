package dev.mapselect.mixin;

import dev.doctor4t.wathe.item.KnifeItem;
import dev.mapselect.role.timemaster.TimeMasterManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KnifeItem.class)
public abstract class TimeMasterKnifeUseMixin {
	@Inject(method = "use", at = @At("RETURN"))
	private void gexpress$recordTimeMasterKnifeReady(World world, PlayerEntity user, Hand hand,
			CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		if (!world.isClient && user instanceof ServerPlayerEntity player) {
			TimeMasterManager.recordWeaponEvent(player, TimeMasterManager.WeaponEventType.KNIFE_READY);
		}
	}
}
