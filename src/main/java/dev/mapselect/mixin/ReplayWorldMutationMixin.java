package dev.mapselect.mixin;

import dev.mapselect.replay.ReplayRecorder;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class ReplayWorldMutationMixin {
    @Inject(method="setBlockState", at=@At("RETURN"))
    private void gexpress$recordBlock(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        WorldChunk chunk=(WorldChunk)(Object)this;
        if (cir.getReturnValue()!=null && chunk.getWorld() instanceof ServerWorld world) ReplayRecorder.changed(world,pos);
    }
}
