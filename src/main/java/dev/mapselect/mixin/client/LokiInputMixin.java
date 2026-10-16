package dev.mapselect.mixin.client;

import dev.mapselect.client.role.loki.ClientLokiState;
import dev.mapselect.role.loki.LokiGravity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class LokiInputMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void gexpress$invertMovement(boolean slowDown,float slowDownFactor,CallbackInfo ci){
        Input input=(Input)(Object)this;var player=MinecraftClient.getInstance().player;
        if(ClientLokiState.hexed()){input.movementForward=-input.movementForward;input.movementSideways=-input.movementSideways;boolean forward=input.pressingForward;input.pressingForward=input.pressingBack;input.pressingBack=forward;boolean left=input.pressingLeft;input.pressingLeft=input.pressingRight;input.pressingRight=left;}
        if(player!=null&&!ClientLokiState.hexed()&&LokiGravity.flipped(player))input.movementSideways=-input.movementSideways;
    }
}
