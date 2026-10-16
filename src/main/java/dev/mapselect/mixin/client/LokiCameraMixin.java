package dev.mapselect.mixin.client;
import dev.mapselect.role.loki.LokiGravity;
import dev.mapselect.client.role.loki.ClientLokiState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Roll the render projection itself so world rendering and its frustum use the same orientation. */
@Mixin(GameRenderer.class)
public abstract class LokiCameraMixin {
    @Inject(method="getBasicProjectionMatrix",at=@At("RETURN"))
    private void gexpress$consistentUpsideDownProjection(CallbackInfoReturnable<Matrix4f> cir){
        var player=MinecraftClient.getInstance().player;
        if(player!=null&&(LokiGravity.flipped(player)||ClientLokiState.hexed()))cir.getReturnValue().rotateZ((float)Math.PI);
    }
}
