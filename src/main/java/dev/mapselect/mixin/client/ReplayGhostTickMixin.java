package dev.mapselect.mixin.client;

import dev.mapselect.client.replay.ClientReplay;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ReplayGhostTickMixin {
    @Inject(method="tickEntity",at=@At("HEAD"),cancellable=true)
    private void gexpress$freezeGhost(Entity entity,CallbackInfo ci) { if(ClientReplay.isGhost(entity)) ci.cancel(); }
}
