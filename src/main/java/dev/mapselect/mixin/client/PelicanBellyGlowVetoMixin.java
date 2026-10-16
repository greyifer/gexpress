package dev.mapselect.mixin.client;

import dev.mapselect.client.role.pelican.ClientVultureState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=Entity.class,priority=100)
public abstract class PelicanBellyGlowVetoMixin {
    @Inject(method="isGlowing",at=@At("RETURN"),cancellable=true)
    private void gexpress$noBellyHighlights(CallbackInfoReturnable<Boolean> cir) {
        if(ClientVultureState.isLocalStashed(MinecraftClient.getInstance())) cir.setReturnValue(false);
    }
}
