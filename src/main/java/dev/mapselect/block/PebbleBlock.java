package dev.mapselect.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

public class PebbleBlock extends Block {
	public static final IntProperty PEBBLES = IntProperty.of("pebbles", 1, 4);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	private static final VoxelShape ONE_PEBBLE_SHAPE = Block.createCuboidShape(5.0, 0.0, 5.0, 11.0, 3.0, 11.0);
	private static final VoxelShape TWO_PEBBLES_SHAPE = VoxelShapes.union(
		Block.createCuboidShape(3.0, 0.0, 6.0, 8.0, 3.0, 11.0),
		Block.createCuboidShape(9.0, 0.0, 4.0, 13.0, 2.5, 8.0)
	);
	private static final VoxelShape THREE_PEBBLES_SHAPE = VoxelShapes.union(
		TWO_PEBBLES_SHAPE,
		Block.createCuboidShape(7.0, 0.0, 9.0, 12.0, 2.0, 14.0)
	);
	private static final VoxelShape FOUR_PEBBLES_SHAPE = VoxelShapes.union(
		THREE_PEBBLES_SHAPE,
		Block.createCuboidShape(5.0, 0.0, 2.0, 10.0, 2.5, 7.0)
	);

	public PebbleBlock(AbstractBlock.Settings settings) {
		super(settings);
		this.setDefaultState(this.stateManager.getDefaultState()
			.with(PEBBLES, 1)
			.with(FACING, Direction.NORTH));
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockState state = ctx.getWorld().getBlockState(ctx.getBlockPos());
		if (state.isOf(this)) {
			return state.with(PEBBLES, Math.min(4, state.get(PEBBLES) + 1));
		}
		return this.getDefaultState().with(FACING, Direction.fromHorizontal(ctx.getWorld().random.nextInt(4)));
	}

	@Override
	protected boolean canReplace(BlockState state, ItemPlacementContext context) {
		return !context.shouldCancelInteraction()
			&& context.getStack().isOf(this.asItem())
			&& state.get(PEBBLES) < 4
			|| super.canReplace(state, context);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return switch (state.get(PEBBLES)) {
			case 2 -> TWO_PEBBLES_SHAPE;
			case 3 -> THREE_PEBBLES_SHAPE;
			case 4 -> FOUR_PEBBLES_SHAPE;
			default -> ONE_PEBBLE_SHAPE;
		};
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(PEBBLES, FACING);
	}
}
