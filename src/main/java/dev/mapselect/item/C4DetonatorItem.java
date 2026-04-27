package dev.mapselect.item;

import dev.mapselect.role.bombspecialist.C4Detonation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class C4DetonatorItem extends Item {
	public C4DetonatorItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient && user instanceof ServerPlayerEntity player) {
			C4Detonation.triggerRemoteDetonation(player);
		}
		return TypedActionResult.success(stack, world.isClient);
	}
}
