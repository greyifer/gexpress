package dev.mapselect.mixin;

import dev.mapselect.role.vengeful.VengefulSpiritManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class VengefulExternalReviveMixin {
    @Inject(method="changeGameMode",at=@At("RETURN"))
    private void gexpress$clearPendingRevive(GameMode mode,CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValueZ() && (mode==GameMode.SURVIVAL || mode==GameMode.ADVENTURE))
            VengefulSpiritManager.onExternalRevive((ServerPlayerEntity)(Object)this);
    }
}
