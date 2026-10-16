package dev.mapselect.mixin;

import dev.mapselect.replay.ReplayManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class ReplayBlockAddedMixin {
    @Inject(method="onBlockAdded", at=@At("HEAD"), cancellable=true)
    private void gexpress$sceneryDoesNotSimulate(World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
        // Scenery is reconstructed a batch at a time; placement callbacks can inspect
        // unloaded neighboring chunks or spawn new entities (pumpkins, portals, etc.).
        if (!world.isClient && ReplayManager.isReplayWorld(world)) ci.cancel();
    }
}
