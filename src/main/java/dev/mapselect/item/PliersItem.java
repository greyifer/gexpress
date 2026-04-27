package dev.mapselect.item;

import dev.mapselect.role.bombspecialist.C4Detonation;
import dev.mapselect.role.bombspecialist.PliersDefuseManager;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The EOD Specialist's one-shot defuse tool. Player and surface C4 both run through the
 * same timed defuse manager so the three second wire cut is visible and cancellable.
 */
public class PliersItem extends Item {
	public PliersItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (!(entity instanceof PlayerEntity target)) return ActionResult.PASS;
		if (user.getWorld().isClient) return ActionResult.SUCCESS;
		return PliersDefuseManager.beginPlayerDefuse(stack, user, target, hand);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) return TypedActionResult.success(stack);
		if (!(user instanceof ServerPlayerEntity serverUser)) return TypedActionResult.pass(stack);
		ItemEntity charge = C4Detonation.findLookedAtCharge(serverUser, 5.0D);
		if (charge == null) return TypedActionResult.pass(stack);
		ActionResult result = PliersDefuseManager.beginBlockDefuse(serverUser, stack, charge, hand);
		return result.isAccepted() ? TypedActionResult.consume(stack) : TypedActionResult.pass(stack);
	}
}
