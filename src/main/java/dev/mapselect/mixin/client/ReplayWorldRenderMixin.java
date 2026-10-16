package dev.mapselect.mixin.client;

import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ReplayWorldRenderMixin {
    @Inject(method="renderWorld",at=@At("HEAD"))
    private void gexpress$interpolateReplay(RenderTickCounter counter,CallbackInfo ci) { dev.mapselect.client.replay.ClientReplay.prepareRender(); }
}
