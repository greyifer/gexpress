package dev.mapselect.block;

import com.mojang.serialization.MapCodec;
import dev.mapselect.leaderboard.LeaderboardManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class LeaderboardBlock extends BlockWithEntity {
	private static final MapCodec<LeaderboardBlock> CODEC = createCodec(LeaderboardBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	private static final double ARROW_WIDTH = 0.45D;
	private static final double ARROW_HEIGHT = 0.30D;

	public LeaderboardBlock(Settings settings) {
		super(settings);
		setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new LeaderboardBlockEntity(pos, state);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
			ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		if (world.isClient) return;
		LeaderboardBlockEntity source = adjacentController(world, pos, state.get(FACING));
		if (source != null && world.getBlockEntity(pos) instanceof LeaderboardBlockEntity placed) {
			placed.setDisplay(source.category(), source.scrollOffset());
		}
		LeaderboardPanel panel = LeaderboardPanel.find(world, pos);
		LeaderboardBlockEntity controller = panel == null ? null : panel.controller(world);
		if (panel != null && controller != null) {
			panel.applyDisplay(world, controller.category(), controller.scrollOffset());
		}
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		LeaderboardPanel panel = LeaderboardPanel.find(world, pos);
		int direction = arrowDirection(panel, hit);
		if (direction == 0) return ActionResult.PASS;
		if (!world.isClient && panel != null && player instanceof ServerPlayerEntity serverPlayer) {
			LeaderboardManager.changeCategory(serverPlayer, panel, direction);
			world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.BLOCKS,
				0.45F, direction < 0 ? 0.92F : 1.08F);
		}
		return ActionResult.SUCCESS;
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
		builder.add(FACING);
	}

	private static int arrowDirection(LeaderboardPanel panel, BlockHitResult hit) {
		if (panel == null || hit == null || hit.getSide() != panel.facing()) return 0;
		double vertical = panel.verticalCoordinate(hit.getPos());
		if (vertical < 0.0D || vertical > ARROW_HEIGHT) return 0;
		double horizontal = panel.screenHorizontalCoordinate(hit.getPos());
		if (horizontal >= 0.0D && horizontal <= ARROW_WIDTH) return -1;
		if (horizontal >= panel.width() - ARROW_WIDTH && horizontal <= panel.width()) return 1;
		return 0;
	}

	private static LeaderboardBlockEntity adjacentController(World world, BlockPos pos, Direction facing) {
		Direction right = facing.rotateYClockwise();
		for (Direction direction : new Direction[]{right, right.getOpposite(), Direction.UP, Direction.DOWN}) {
			BlockPos neighborPos = pos.offset(direction);
			BlockState neighborState = world.getBlockState(neighborPos);
			if (neighborState.isOf(dev.mapselect.registry.MapSelectBlocks.LEADERBOARD)
					&& neighborState.get(FACING) == facing
					&& world.getBlockEntity(neighborPos) instanceof LeaderboardBlockEntity leaderboard) {
				return leaderboard;
			}
		}
		return null;
	}
}
