package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.cca.TrainWorldComponent;
import dev.mapselect.client.replay.ClientReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(TrainWorldComponent.class)
public abstract class ReplayTrainClockMixin {
    @Shadow @Final private World world;
    @Inject(method = "readFromNbt", at = @At("HEAD"), cancellable = true)
    private void gexpress$ignoreStageTrainSync(CallbackInfo ci) {
        if (ClientReplay.isReplayWorld(world) && ClientReplay.hasTrain && !ClientReplay.applyingTrainSnapshot()) ci.cancel();
    }
    @Inject(method="clientTick", at=@At("HEAD"), cancellable=true)
    private void gexpress$recordedClock(CallbackInfo ci) {
        if (ClientReplay.isReplayWorld(world) && ClientReplay.hasTrain) ci.cancel();
    }
    @Inject(method="getTime", at=@At("HEAD"), cancellable=true)
    private void gexpress$renderClock(CallbackInfoReturnable<Float> cir) {
        if (ClientReplay.isReplayWorld(world) && ClientReplay.hasTrain) {
            // Wathe adds render tick delta itself, including while the replay is paused.
            cir.setReturnValue(ClientReplay.sceneryTime() - MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true));
        }
    }
}
