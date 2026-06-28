package dev.mapselect.item;

import dev.mapselect.tutorial.TutorialManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public final class TutorialWeaponItem extends Item {
	public enum Kind { REVOLVER, KNIFE }

	private final Kind kind;

	public TutorialWeaponItem(Kind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient && user instanceof ServerPlayerEntity player) {
			TutorialManager.useWeapon(player, kind);
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (!user.getWorld().isClient && user instanceof ServerPlayerEntity player) {
			TutorialManager.useWeapon(player, kind);
		}
		return ActionResult.success(user.getWorld().isClient);
	}
}
