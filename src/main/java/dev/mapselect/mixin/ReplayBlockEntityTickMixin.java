package dev.mapselect.mixin;

import dev.mapselect.replay.ReplayManager;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class ReplayBlockEntityTickMixin {
    @Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
    private void gexpress$keepReplayBlockEntitiesRecorded(CallbackInfo ci) {
        World world = (World)(Object)this;
        if (!world.isClient && ReplayManager.isReplayWorld(world)) ci.cancel();
    }
}
