package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.client.replay.ClientReplay;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameWorldComponent.class)
public abstract class ReplayMapEffectSyncMixin {
    @Shadow @Final private World world;
    @Inject(method = "readFromNbt", at = @At("TAIL"))
    private void gexpress$keepRecordedMapEffect(CallbackInfo ci) {
        if (ClientReplay.isReplayWorld(world)) ClientReplay.restoreMapEffect((GameWorldComponent)(Object)this);
    }
}
