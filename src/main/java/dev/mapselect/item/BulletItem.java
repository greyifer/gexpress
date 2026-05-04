package dev.mapselect.item;

import dev.mapselect.role.mafia.MafiaManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class BulletItem extends Item {
	public BulletItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) return TypedActionResult.success(stack);
		if (user instanceof ServerPlayerEntity serverPlayer && MafiaManager.tryLoadBullet(serverPlayer, stack)) {
			return TypedActionResult.consume(stack);
		}
		return TypedActionResult.pass(stack);
	}
}
