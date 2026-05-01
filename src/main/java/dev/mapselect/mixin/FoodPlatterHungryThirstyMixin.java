package dev.mapselect.mixin;

import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.modifier.ModifierUtils;
import dev.mapselect.registry.MapSelectModifiers;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(FoodPlatterBlock.class)
public abstract class FoodPlatterHungryThirstyMixin {
	@Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
	private void gexpress$allowExtraHungryThirstyPickups(BlockState state, @NotNull World world, BlockPos pos,
			PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
		if (world.isClient || player.isCreative()) return;
		if (!(world.getBlockEntity(pos) instanceof BeveragePlateBlockEntity plate)) return;
		if (!player.getStackInHand(Hand.MAIN_HAND).isEmpty()) return;
		if (player.getStackInHand(Hand.MAIN_HAND).isOf(WatheItems.POISON_VIAL)) return;

		boolean drink = plate.isDrink();
		boolean modified = drink
			? ModifierUtils.has(player, MapSelectModifiers.THIRSTY_ID)
			: ModifierUtils.has(player, MapSelectModifiers.HUNGRY_ID);
		if (!modified) return;

		List<ItemStack> platter = plate.getStoredItems();
		if (platter.isEmpty()) {
			cir.setReturnValue(ActionResult.SUCCESS);
			return;
		}

		int limit = drink ? GexpressConfig.getThirstyDrinkLimit() : GexpressConfig.getHungryFoodLimit();
		if (countMatchingPlatterItems(player, platter) >= limit) {
			cir.setReturnValue(ActionResult.SUCCESS);
			return;
		}

		ItemStack randomItem = platter.get(world.random.nextInt(platter.size())).copy();
		randomItem.setCount(1);
		randomItem.set(DataComponentTypes.MAX_STACK_SIZE, 1);
		String poisoner = plate.getPoisoner();
		if (poisoner != null) {
			randomItem.set(WatheDataComponentTypes.POISONER, poisoner);
			plate.setPoisoner(null);
		}
		player.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 1.0F, 1.0F);
		player.setStackInHand(Hand.MAIN_HAND, randomItem);
		cir.setReturnValue(ActionResult.SUCCESS);
	}

	private static int countMatchingPlatterItems(PlayerEntity player, List<ItemStack> platter) {
		int count = 0;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isEmpty()) continue;
			for (ItemStack platterItem : platter) {
				if (!platterItem.isEmpty() && stack.getItem() == platterItem.getItem()) {
					count += stack.getCount();
					break;
				}
			}
		}
		return count;
	}
}
