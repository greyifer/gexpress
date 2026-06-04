package dev.mapselect.mixin;

import dev.mapselect.block.FusedOrnamentBlock;
import dev.mapselect.registry.MapSelectBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class FusedOrnamentStepSoundMixin {
	@Shadow
	public abstract World getWorld();

	@Redirect(
		method = "playStepSound",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getSoundGroup()Lnet/minecraft/sound/BlockSoundGroup;")
	)
	private BlockSoundGroup gexpress$useDecoratedBlockSound(BlockState state, BlockPos pos, BlockState stepState) {
		if (state.isOf(MapSelectBlocks.FUSED_ORNAMENTED_BLOCK)) {
			BlockState baseState = FusedOrnamentBlock.getBaseState(getWorld(), pos);
			if (baseState != null) {
				return baseState.getSoundGroup();
			}
		}
		return state.getSoundGroup();
	}
}
