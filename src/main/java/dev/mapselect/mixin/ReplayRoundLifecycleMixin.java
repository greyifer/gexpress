package dev.mapselect.mixin;

import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.replay.ReplayRecorder;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameFunctions.class)
public abstract class ReplayRoundLifecycleMixin {
    @Inject(method = "finalizeGame", at = @At("HEAD"), remap = false)
    private static void gexpress$saveBeforeLobbyReset(ServerWorld world, CallbackInfo ci) {
        ReplayRecorder.finish(world);
    }
}
