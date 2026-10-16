package dev.mapselect.block;

import com.mojang.serialization.MapCodec;
import dev.mapselect.role.rat.RatManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.block.ShapeContext;

public final class RatTrapBlock extends Block {
	public static final MapCodec<RatTrapBlock> CODEC = createCodec(RatTrapBlock::new);
	public static final int LIFETIME_TICKS = 60 * 20;
	private static final int FADE_STEPS = 60;
	public static final IntProperty AGE = IntProperty.of("age", 0, FADE_STEPS - 1);
	private static final int FADE_STEP_TICKS = LIFETIME_TICKS / FADE_STEPS;
	private static final VoxelShape OUTLINE =
		VoxelShapes.cuboid(0.30D, 0.0D, 0.30D, 0.70D, 0.0625D, 0.70D);

	public RatTrapBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(AGE, 0));
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return OUTLINE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (!world.isClient && !oldState.isOf(this)) {
			world.scheduleBlockTick(pos, this, FADE_STEP_TICKS);
		}
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (!state.isOf(this)) return;
		int age = state.get(AGE);
		if (age >= AGE.getValues().size() - 1) {
			world.breakBlock(pos, false);
			return;
		}
		world.setBlockState(pos, state.with(AGE, age + 1), Block.NOTIFY_ALL);
		world.scheduleBlockTick(pos, this, FADE_STEP_TICKS);
	}

	/** Remaining opacity over the trap's fixed one-minute lifetime. */
	public static float fadeProgress(BlockState state) {
		if (state == null || !state.contains(AGE)) return 1.0F;
		return Math.max(1.0F / FADE_STEPS, 1.0F - state.get(AGE) / (float) FADE_STEPS);
	}

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (!(world instanceof ServerWorld serverWorld)
				|| !(entity instanceof ServerPlayerEntity player)
				|| !RatManager.isRat(player)) {
			return;
		}
		serverWorld.breakBlock(pos, false);
		RatManager.killWithTrap(player);
	}
}
