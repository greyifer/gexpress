package dev.mapselect.mixin.client;

import dev.mapselect.client.role.twins.ClientTwinsBodies;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class TwinFakePlayerSkinMixin {
    @Inject(method="getSkinTextures",at=@At("HEAD"),cancellable=true)
    private void gexpress$ownerSkin(CallbackInfoReturnable<SkinTextures> cir){
        var owner=ClientTwinsBodies.owner((AbstractClientPlayerEntity)(Object)this);var network=MinecraftClient.getInstance().getNetworkHandler();
        var entry=owner==null || network==null?null:network.getPlayerListEntry(owner);if(entry!=null)cir.setReturnValue(entry.getSkinTextures());
    }
}
