package dev.mapselect.mixin;

import dev.mapselect.role.loki.LokiGravity;
import net.minecraft.server.network.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class LokiFloatingMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow private boolean floating;
    @Inject(method="onPlayerMove",at=@At("RETURN"))
    private void gexpress$upwardGravityIsNotFlying(PlayerMoveC2SPacket packet,CallbackInfo ci){if(LokiGravity.flipped(player))floating=false;}
}
