package dev.mapselect.mixin;

import dev.mapselect.block.FusedOrnamentBlock;
import dev.mapselect.registry.MapSelectBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PlayerEntity.class)
public abstract class FusedOrnamentPlayerStepSoundMixin {
	@ModifyArg(
		method = "playStepSound",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;playStepSound(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V"),
		index = 1
	)
	private BlockState gexpress$stepOnDecoratedBaseBlock(BlockState state) {
		if (!state.isOf(MapSelectBlocks.FUSED_ORNAMENTED_BLOCK)) return state;
		PlayerEntity player = (PlayerEntity) (Object) this;
		BlockState baseState = gexpress$getDecoratedBaseState(player, player.getSteppingPos());
		if (baseState == null) baseState = gexpress$getDecoratedBaseState(player, player.getBlockPos());
		if (baseState == null) baseState = gexpress$getDecoratedBaseState(player, player.getBlockPos().down());
		return baseState == null ? state : baseState;
	}

	private static BlockState gexpress$getDecoratedBaseState(PlayerEntity player, BlockPos pos) {
		return FusedOrnamentBlock.getBaseState(player.getWorld(), pos);
	}
}
