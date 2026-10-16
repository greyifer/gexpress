package dev.mapselect.block;

import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.mapselect.registry.MapSelectBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class LargeWoodDoorBlockEntity extends DoorBlockEntity {
	public LargeWoodDoorBlockEntity(BlockPos pos, BlockState state) {
		super(MapSelectBlockEntities.LARGE_WOOD_DOOR, pos, state);
	}

	public static void clientTick(World world, BlockPos pos, BlockState state, LargeWoodDoorBlockEntity entity) {
		DoorBlockEntity.clientTick(world, pos, state, entity);
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, LargeWoodDoorBlockEntity entity) {
		if (state.get(LargeWoodDoorBlock.OPEN) && !entity.isBlasted()) {
			entity.setCloseCountdown(entity.getCloseCountdown() - 1);
			if (entity.getCloseCountdown() <= 0) {
				LargeWoodDoorBlock.toggleDoor(state, world, entity, pos);
			}
		} else {
			entity.setCloseCountdown(0);
		}

		if (entity.isJammed()) {
			entity.setJammed(entity.getJammedTime() - 1);
		}
	}

	@Override
	protected void toggleOpen() {
		super.toggleOpen();
		LargeWoodDoorBlockEntity neighbor = LargeWoodDoorBlock.getNeighborDoorEntity(getCachedState(), world, pos);
		if (neighbor != null) {
			neighbor.toggle(true);
		}
	}

	@Override
	protected void toggleBlocks() {
		if (world == null) return;
		boolean open = isOpen();
		BlockPos lowerPos = getPos();
		for (LargeDoorPart part : LargeDoorPart.values()) {
			BlockPos partPos = lowerPos.up(part.offsetFromLower());
			BlockState partState = world.getBlockState(partPos);
			if (partState.isOf(getCachedState().getBlock())) {
				world.setBlockState(partPos, partState.with(LargeWoodDoorBlock.OPEN, open), Block.NOTIFY_ALL);
			}
		}
	}
}
