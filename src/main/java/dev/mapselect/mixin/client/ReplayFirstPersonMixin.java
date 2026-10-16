package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.mapselect.client.replay.ClientReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ReplayFirstPersonMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private HeldItemRenderer firstPersonRenderer;
    @Shadow @Final private LightmapTextureManager lightmapTextureManager;
    @Inject(method="renderHand",at=@At(value="INVOKE",target="Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
    private void gexpress$recordedHands(Camera camera,float tickDelta,Matrix4f position,CallbackInfo ci,@Local MatrixStack matrices) {
        if(!ClientReplay.active || !client.options.getPerspective().isFirstPerson() || client.options.hudHidden
            || !(camera.getFocusedEntity() instanceof AbstractClientPlayerEntity player) || !ClientReplay.isGhost(player) || player.isSpectator()) return;
        var consumers=client.getBufferBuilders().getEntityVertexConsumers();
        var renderer=(ReplayHeldItemInvoker)firstPersonRenderer;
        int light=client.getEntityRenderDispatcher().getLight(player,tickDelta);
        lightmapTextureManager.enable();
        try {
            renderer.gexpress$replayHand(player,tickDelta,player.getPitch(),Hand.MAIN_HAND,player.getHandSwingProgress(tickDelta),player.getMainHandStack(),0,matrices,consumers,light);
            renderer.gexpress$replayHand(player,tickDelta,player.getPitch(),Hand.OFF_HAND,0,player.getOffHandStack(),0,matrices,consumers,light);
            consumers.draw();
        } finally { lightmapTextureManager.disable(); }
    }
}
