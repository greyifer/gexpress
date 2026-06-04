package dev.mapselect.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

public class GoldLedgeBlock extends Block {
	public static final MapCodec<GoldLedgeBlock> CODEC = createCodec(GoldLedgeBlock::new);
	public static final BooleanProperty NORTH = BooleanProperty.of("north");
	public static final BooleanProperty EAST = BooleanProperty.of("east");
	public static final BooleanProperty SOUTH = BooleanProperty.of("south");
	public static final BooleanProperty WEST = BooleanProperty.of("west");
	public static final BooleanProperty TOP = BooleanProperty.of("top");
	private static final Direction[] HORIZONTALS = {
		Direction.NORTH,
		Direction.EAST,
		Direction.SOUTH,
		Direction.WEST
	};
	private static final VoxelShape NORTH_OUTLINE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 2.0, 2.0);
	private static final VoxelShape EAST_OUTLINE = Block.createCuboidShape(14.0, 0.0, 0.0, 16.0, 2.0, 16.0);
	private static final VoxelShape SOUTH_OUTLINE = Block.createCuboidShape(0.0, 0.0, 14.0, 16.0, 2.0, 16.0);
	private static final VoxelShape WEST_OUTLINE = Block.createCuboidShape(0.0, 0.0, 0.0, 2.0, 2.0, 16.0);
	private static final VoxelShape NORTH_TOP_OUTLINE = Block.createCuboidShape(0.0, 14.0, 0.0, 16.0, 16.0, 2.0);
	private static final VoxelShape EAST_TOP_OUTLINE = Block.createCuboidShape(14.0, 14.0, 0.0, 16.0, 16.0, 16.0);
	private static final VoxelShape SOUTH_TOP_OUTLINE = Block.createCuboidShape(0.0, 14.0, 14.0, 16.0, 16.0, 16.0);
	private static final VoxelShape WEST_TOP_OUTLINE = Block.createCuboidShape(0.0, 14.0, 0.0, 2.0, 16.0, 16.0);
	private static final VoxelShape NORTH_COLLISION = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 8.0, 8.0);
	private static final VoxelShape EAST_COLLISION = Block.createCuboidShape(8.0, 0.0, 0.0, 16.0, 8.0, 16.0);
	private static final VoxelShape SOUTH_COLLISION = Block.createCuboidShape(0.0, 0.0, 8.0, 16.0, 8.0, 16.0);
	private static final VoxelShape WEST_COLLISION = Block.createCuboidShape(0.0, 0.0, 0.0, 8.0, 8.0, 16.0);
	private static final VoxelShape NORTH_TOP_COLLISION = Block.createCuboidShape(0.0, 8.0, 0.0, 16.0, 16.0, 8.0);
	private static final VoxelShape EAST_TOP_COLLISION = Block.createCuboidShape(8.0, 8.0, 0.0, 16.0, 16.0, 16.0);
	private static final VoxelShape SOUTH_TOP_COLLISION = Block.createCuboidShape(0.0, 8.0, 8.0, 16.0, 16.0, 16.0);
	private static final VoxelShape WEST_TOP_COLLISION = Block.createCuboidShape(0.0, 8.0, 0.0, 8.0, 16.0, 16.0);

	public GoldLedgeBlock(AbstractBlock.Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState()
			.with(NORTH, true)
			.with(EAST, false)
			.with(SOUTH, false)
			.with(WEST, false)
			.with(TOP, false));
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, TOP);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		BlockState existing = context.getWorld().getBlockState(context.getBlockPos());
		Direction direction = context.getHorizontalPlayerFacing();
		boolean top = isTopPlacement(context);
		if (existing.isOf(this)) {
			if (existing.get(TOP) != top) return null;
			if (!existing.get(property(direction))) return existing.with(property(direction), true);
			for (Direction horizontal : HORIZONTALS) {
				if (!existing.get(property(horizontal))) return existing.with(property(horizontal), true);
			}
			return existing;
		}
		return getDefaultState()
			.with(NORTH, false)
			.with(EAST, false)
			.with(SOUTH, false)
			.with(WEST, false)
			.with(TOP, top)
			.with(property(direction), true);
	}

	@Override
	protected boolean canReplace(BlockState state, ItemPlacementContext context) {
		if (!context.getStack().isOf(asItem()) || context.shouldCancelInteraction()) return false;
		if (state.get(TOP) != isTopPlacement(context)) return false;
		for (Direction direction : HORIZONTALS) {
			if (!state.get(property(direction))) return true;
		}
		return false;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return combinedShape(state, false);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return combinedShape(state, true);
	}

	private static VoxelShape combinedShape(BlockState state, boolean collision) {
		VoxelShape shape = VoxelShapes.empty();
		boolean top = state.get(TOP);
		if (state.get(NORTH)) shape = VoxelShapes.union(shape, shape(Direction.NORTH, top, collision));
		if (state.get(EAST)) shape = VoxelShapes.union(shape, shape(Direction.EAST, top, collision));
		if (state.get(SOUTH)) shape = VoxelShapes.union(shape, shape(Direction.SOUTH, top, collision));
		if (state.get(WEST)) shape = VoxelShapes.union(shape, shape(Direction.WEST, top, collision));
		return shape;
	}

	private static VoxelShape shape(Direction direction, boolean top, boolean collision) {
		return switch (direction) {
			case EAST -> top ? (collision ? EAST_TOP_COLLISION : EAST_TOP_OUTLINE) : (collision ? EAST_COLLISION : EAST_OUTLINE);
			case SOUTH -> top ? (collision ? SOUTH_TOP_COLLISION : SOUTH_TOP_OUTLINE) : (collision ? SOUTH_COLLISION : SOUTH_OUTLINE);
			case WEST -> top ? (collision ? WEST_TOP_COLLISION : WEST_TOP_OUTLINE) : (collision ? WEST_COLLISION : WEST_OUTLINE);
			default -> top ? (collision ? NORTH_TOP_COLLISION : NORTH_TOP_OUTLINE) : (collision ? NORTH_COLLISION : NORTH_OUTLINE);
		};
	}

	private static boolean isTopPlacement(ItemPlacementContext context) {
		if (context.getSide() == Direction.UP) return false;
		double localY = context.getHitPos().y - context.getBlockPos().getY();
		return localY > 0.5D;
	}

	private static BooleanProperty property(Direction direction) {
		return switch (direction) {
			case EAST -> EAST;
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			default -> NORTH;
		};
	}
}
