package dev.mapselect.block;

import com.mojang.serialization.MapCodec;
import dev.doctor4t.wathe.block.property.OrnamentShape;
import dev.doctor4t.wathe.index.WatheProperties;
import dev.doctor4t.wathe.mixin.AbstractBlockInvoker;
import dev.mapselect.item.FusedOrnamentItem;
import dev.mapselect.registry.MapSelectBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class FusedOrnamentBlock extends BlockWithEntity {
	public static final EnumProperty<OrnamentShape> SHAPE = WatheProperties.ORNAMENT_SHAPE;

	public FusedOrnamentBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState()
			.with(FacingBlock.FACING, Direction.NORTH)
			.with(SHAPE, OrnamentShape.CENTER));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return null;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new FusedOrnamentBlockEntity(pos, state);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof FusedOrnamentItem ornamentItem) {
			return ornamentItem.placeOrMerge(stack, state, world, pos, player, hit).isAccepted()
				? ItemActionResult.SUCCESS
				: ItemActionResult.FAIL;
		}
		if (stack.getItem() instanceof BlockItem) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		BlockState baseState = getBaseState(world, pos);
		if (baseState == null) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		return ((AbstractBlockInvoker) baseState.getBlock()).wathe$invokeOnUseWithItem(
			stack, baseState, world, pos, player, hand, hit);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player.shouldCancelInteraction() && player.getMainHandStack().isEmpty()) {
			return removeOrnament(state, world, pos, player, hit.getSide(), null);
		}
		BlockState baseState = getBaseState(world, pos);
		if (baseState == null) return ActionResult.PASS;
		return ((AbstractBlockInvoker) baseState.getBlock()).wathe$invokeOnUse(baseState, world, pos, player, hit);
	}

	public static ActionResult removeOrnament(BlockState state, World world, BlockPos pos, @Nullable PlayerEntity player,
			Direction hitSide, @Nullable Identifier preferredOrnamentId) {
		if (!(world.getBlockEntity(pos) instanceof FusedOrnamentBlockEntity entity)) return ActionResult.PASS;
		Direction side = hitSide;
		FusedOrnamentBlockEntity.Decoration decoration = entity.getDecoration(side);
		if (preferredOrnamentId != null && (decoration == null || !preferredOrnamentId.equals(decoration.ornamentId()))) {
			Map.Entry<Direction, FusedOrnamentBlockEntity.Decoration> match = entity.getDecorations().entrySet().stream()
				.filter(entry -> preferredOrnamentId.equals(entry.getValue().ornamentId()))
				.findFirst()
				.orElse(null);
			if (match != null) {
				side = match.getKey();
				decoration = match.getValue();
			}
		}
		if (decoration == null && preferredOrnamentId == null && entity.getDecorations().size() == 1) {
			Map.Entry<Direction, FusedOrnamentBlockEntity.Decoration> first = entity.getDecorations().entrySet().iterator().next();
			side = first.getKey();
			decoration = first.getValue();
		}
		if (preferredOrnamentId != null && (decoration == null || !preferredOrnamentId.equals(decoration.ornamentId()))) {
			return ActionResult.PASS;
		}
		if (decoration == null) return ActionResult.PASS;
		if (!world.isClient) {
			BlockState baseState = entity.getBaseState();
			FusedOrnamentBlockEntity.Decoration removed = decoration;
			entity.removeDecoration(side);
			if (entity.hasDecorations()) {
				Map.Entry<Direction, FusedOrnamentBlockEntity.Decoration> first = entity.getDecorations().entrySet().iterator().next();
				world.setBlockState(pos, state
					.with(FacingBlock.FACING, first.getKey())
					.with(SHAPE, first.getValue().shape()), Block.NOTIFY_ALL);
				if (world.getBlockEntity(pos) instanceof FusedOrnamentBlockEntity syncedEntity) syncedEntity.sync();
			} else {
				world.setBlockState(pos, baseState, Block.NOTIFY_ALL);
			}
			if (player == null || !player.isCreative()) {
				dropDecoration(world, pos, removed);
			}
		}
		return ActionResult.success(world.isClient);
	}

	public static OrnamentShape shapeFromHit(Vec2f hit, boolean center) {
		boolean topRight = hit.x + hit.y > 1;
		boolean bottomRight = hit.x - hit.y > 0;
		return center ? OrnamentShape.CENTER :
			topRight && bottomRight ? OrnamentShape.RIGHT :
				topRight ? OrnamentShape.TOP :
					!bottomRight ? OrnamentShape.LEFT : OrnamentShape.BOTTOM;
	}

	@Override
	public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state,
			@Nullable BlockEntity blockEntity, ItemStack tool) {
		if (!world.isClient && !player.isCreative() && blockEntity instanceof FusedOrnamentBlockEntity entity
				&& world instanceof ServerWorld serverWorld) {
			for (ItemStack drop : Block.getDroppedStacks(entity.getBaseState(), serverWorld, pos, null, player, tool)) {
				Block.dropStack(world, pos, drop);
			}
			for (FusedOrnamentBlockEntity.Decoration decoration : entity.getDecorations().values()) {
				dropDecoration(world, pos, decoration);
			}
		}
		super.afterBreak(world, player, pos, state, blockEntity, tool);
	}

	@Override
	protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
		return List.of();
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		BlockState baseState = getBaseState(world, pos);
		return baseState == null ? super.getOutlineShape(state, world, pos, context)
			: baseState.getOutlineShape(world, pos, context);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		BlockState baseState = getBaseState(world, pos);
		VoxelShape baseShape = baseState == null ? super.getCollisionShape(state, world, pos, context)
			: baseState.getCollisionShape(world, pos, context);
		return hasGoldLedge(world, pos) ? VoxelShapes.union(baseShape, getFusedGoldLedgeCollisionShape(world, pos, state)) : baseShape;
	}

	@Override
	protected VoxelShape getSidesShape(BlockState state, BlockView world, BlockPos pos) {
		BlockState baseState = getBaseState(world, pos);
		return baseState == null ? super.getSidesShape(state, world, pos)
			: baseState.getSidesShape(world, pos);
	}

	@Override
	protected VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
		BlockState baseState = getBaseState(world, pos);
		return baseState == null ? super.getRaycastShape(state, world, pos)
			: baseState.getRaycastShape(world, pos);
	}

	@Override
	protected VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos,
			ShapeContext context) {
		BlockState baseState = getBaseState(world, pos);
		return baseState == null ? super.getCameraCollisionShape(state, world, pos, context)
			: baseState.getCameraCollisionShape(world, pos, context);
	}

	@Override
	protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
		BlockState baseState = getBaseState(world, pos);
		return baseState == null ? super.calcBlockBreakingDelta(state, player, world, pos)
			: baseState.calcBlockBreakingDelta(player, world, pos);
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		BlockState baseState = getBaseState(world, pos);
		return baseState == null || baseState.canPlaceAt(world, pos);
	}

	@Override
	protected boolean canPathfindThrough(BlockState state, NavigationType type) {
		return false;
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
			WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		BlockState baseState = getBaseState(world, pos);
		if (baseState == null) return state;
		return baseState.getStateForNeighborUpdate(direction, neighborState, world, pos, neighborPos) == baseState
			? state
			: state;
	}

	@Override
	protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
		BlockState baseState = getBaseState(world, pos);
		return baseState == null ? 0 : baseState.getWeakRedstonePower(world, pos, direction);
	}

	@Override
	protected int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
		BlockState baseState = getBaseState(world, pos);
		return baseState == null ? 0 : baseState.getStrongRedstonePower(world, pos, direction);
	}

	@Override
	protected boolean emitsRedstonePower(BlockState state) {
		return true;
	}

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		BlockState baseState = getBaseState(world, pos);
		if (baseState != null) baseState.onEntityCollision(world, pos, entity);
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FacingBlock.FACING, SHAPE);
	}

	@Nullable
	public static BlockState getBaseState(BlockView world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof FusedOrnamentBlockEntity entity ? entity.getBaseState() : null;
	}

	private static boolean hasGoldLedge(BlockView world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof FusedOrnamentBlockEntity entity
			&& entity.hasGoldLedgeCollision();
	}

	private static VoxelShape getFusedGoldLedgeCollisionShape(BlockView world, BlockPos pos, BlockState state) {
		if (world.getBlockEntity(pos) instanceof FusedOrnamentBlockEntity entity) {
			return entity.getGoldLedgeCollisionShape();
		}
		return FusedOrnamentGeometry.goldLedgeCollisionShape(state.get(FacingBlock.FACING), state.get(SHAPE));
	}

	private static void dropDecoration(World world, BlockPos pos, FusedOrnamentBlockEntity.Decoration decoration) {
		Block.dropStack(world, pos, new ItemStack(
			MapSelectBlocks.itemForOrnament(decoration.ornamentId()),
			Math.max(1, decoration.shape().getCount())
		));
	}

	public static boolean canDecorate(BlockState state, World world, BlockPos pos) {
		return !state.isAir()
			&& !state.isOf(MapSelectBlocks.FUSED_ORNAMENTED_BLOCK)
			&& !state.isReplaceable()
			&& world.getBlockEntity(pos) == null;
	}
}
