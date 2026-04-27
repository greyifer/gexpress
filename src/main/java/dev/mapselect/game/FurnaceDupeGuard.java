package dev.mapselect.game;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class FurnaceDupeGuard {
	private FurnaceDupeGuard() {}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient || !isActiveGame(world)) return ActionResult.PASS;
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (!isFurnaceLike(world, pos, state)) return ActionResult.PASS;
			return ActionResult.FAIL;
		});
	}

	private static boolean isActiveGame(World world) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
	}

	private static boolean isFurnaceLike(World world, BlockPos pos, BlockState state) {
		return state.isOf(Blocks.FURNACE)
			|| state.isOf(Blocks.BLAST_FURNACE)
			|| state.isOf(Blocks.SMOKER)
			|| world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity;
	}
}
