package dev.mapselect.block;

import com.mojang.serialization.MapCodec;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class FloatingTextBlock extends BlockWithEntity {
	private static final MapCodec<FloatingTextBlock> CODEC = createCodec(FloatingTextBlock::new);
	private static final VoxelShape OUTLINE = createCuboidShape(0, 0, 0, 16, 16, 16);

	public FloatingTextBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new FloatingTextBlockEntity(pos, state);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return OUTLINE;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
		if (!GexpressPermissions.canEditSetupOptions(serverPlayer)) {
			serverPlayer.sendMessage(Text.literal("You need setup permission to edit floating text.")
				.formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}
		FloatingTextManager.openEditor(serverPlayer, pos);
		return ActionResult.SUCCESS;
	}
}
