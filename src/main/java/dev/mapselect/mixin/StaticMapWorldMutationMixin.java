package dev.mapselect.mixin;

import dev.mapselect.game.StaticMapResetManager;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class StaticMapWorldMutationMixin {
	@Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
		at = @At("HEAD"))
	private void gexpress$captureStaticMapOriginal(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
			CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerWorld world) {
			StaticMapResetManager.captureOriginal(world, pos, state);
		}
	}
}
