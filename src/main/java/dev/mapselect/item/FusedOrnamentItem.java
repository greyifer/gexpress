package dev.mapselect.item;

import dev.doctor4t.wathe.block.property.OrnamentShape;
import dev.doctor4t.wathe.util.BlockUtils;
import dev.mapselect.block.FusedOrnamentBlock;
import dev.mapselect.block.FusedOrnamentBlockEntity;
import dev.mapselect.registry.MapSelectBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class FusedOrnamentItem extends Item {
	private final Identifier ornamentId;
	private final boolean centerPlacement;

	public FusedOrnamentItem(Settings settings, Identifier ornamentId) {
		this(settings, ornamentId, true);
	}

	public FusedOrnamentItem(Settings settings, Identifier ornamentId, boolean centerPlacement) {
		super(settings);
		this.ornamentId = ornamentId;
		this.centerPlacement = centerPlacement;
	}

	public Identifier getOrnamentId() {
		return ornamentId;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		return placeOrMerge(
			context.getStack(),
			context.getWorld().getBlockState(context.getBlockPos()),
			context.getWorld(),
			context.getBlockPos(),
			context.getPlayer(),
			context.getBlockPos(),
			context.getSide(),
			context.getHitPos()
		);
	}

	public ActionResult placeOrMerge(ItemStack stack, BlockState clickedState, World world, BlockPos pos,
			PlayerEntity player, BlockHitResult hitResult) {
		return placeOrMerge(stack, clickedState, world, pos, player, hitResult.getBlockPos(),
			hitResult.getSide(), hitResult.getPos());
	}

	private ActionResult placeOrMerge(ItemStack stack, BlockState clickedState, World world, BlockPos pos,
			PlayerEntity player, BlockPos hitBlockPos, Direction side, Vec3d hitPos) {
		if (player != null && player.shouldCancelInteraction()
				&& clickedState.isOf(MapSelectBlocks.FUSED_ORNAMENTED_BLOCK)) {
			return FusedOrnamentBlock.removeOrnament(clickedState, world, pos, player, side, ornamentId);
		}

		Vec2f hit = BlockUtils.get2DHit(hitPos, hitBlockPos, side);
		OrnamentShape shape = FusedOrnamentBlock.shapeFromHit(hit,
			centerPlacement && player != null && player.shouldCancelInteraction());

		if (clickedState.isOf(MapSelectBlocks.FUSED_ORNAMENTED_BLOCK)) {
			if (!(world.getBlockEntity(pos) instanceof FusedOrnamentBlockEntity entity)) return ActionResult.FAIL;
			FusedOrnamentBlockEntity.Decoration existing = entity.getDecoration(side);
			if (existing != null && !ornamentId.equals(existing.ornamentId())) return ActionResult.FAIL;
			OrnamentShape merged = existing == null ? shape : existing.shape().with(shape);
			if (existing != null && existing.shape() == merged) return ActionResult.FAIL;
			if (!world.isClient) {
				world.setBlockState(pos, clickedState
					.with(FacingBlock.FACING, side)
					.with(FusedOrnamentBlock.SHAPE, merged), Block.NOTIFY_ALL);
				if (world.getBlockEntity(pos) instanceof FusedOrnamentBlockEntity syncedEntity) {
					syncedEntity.setDecoration(side, ornamentId, merged);
				}
				if (player == null || !player.isCreative()) stack.decrement(1);
			}
			return ActionResult.success(world.isClient);
		}

		if (!FusedOrnamentBlock.canDecorate(clickedState, world, pos)) return ActionResult.FAIL;
		if (!world.isClient) {
			BlockState ornamentedState = MapSelectBlocks.FUSED_ORNAMENTED_BLOCK.getDefaultState()
				.with(FacingBlock.FACING, side)
				.with(FusedOrnamentBlock.SHAPE, shape);
			world.setBlockState(pos, ornamentedState, Block.NOTIFY_ALL);
			if (world.getBlockEntity(pos) instanceof FusedOrnamentBlockEntity entity) {
				entity.setBaseState(clickedState);
				entity.setDecoration(side, ornamentId, shape);
			}
			if (player == null || !player.isCreative()) stack.decrement(1);
		}
		return ActionResult.success(world.isClient);
	}
}
