package dev.mapselect.block;

import com.mojang.serialization.MapCodec;
import dev.mapselect.registry.MapSelectBlockEntities;
import dev.mapselect.registry.MapSelectSounds;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class GreyiferPlushBlock extends BlockWithEntity implements BlockEntityProvider {
	private static final MapCodec<GreyiferPlushBlock> CODEC = createCodec(GreyiferPlushBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = BlockWithEntity.createCuboidShape(2.0, 0.0, 2.0, 14.0, 15.0, 14.0);

	public GreyiferPlushBlock(AbstractBlock.Settings settings) {
		super(settings);
		this.setDefaultState(this.stateManager.getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new GreyiferPlushBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, MapSelectBlockEntities.GREYIFER_PLUSH, GreyiferPlushBlockEntity::tick);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.ENTITYBLOCK_ANIMATED;
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient && world.getBlockEntity(pos) instanceof GreyiferPlushBlockEntity plush) {
			plush.squish(24);
		}
		return super.onBreak(world, pos, state, player);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient) {
			float pitch = 0.8F + world.getRandom().nextFloat() * 0.4F;
			world.playSound(null, pos, MapSelectSounds.GREYIFER_PLUSH_HONK, SoundCategory.BLOCKS, 1.0F, pitch);
			if (world.getBlockEntity(pos) instanceof GreyiferPlushBlockEntity plush) {
				plush.squish(1);
			}
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
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
