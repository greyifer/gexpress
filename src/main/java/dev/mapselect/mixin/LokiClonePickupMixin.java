package dev.mapselect.mixin;

import dev.mapselect.role.loki.LokiManager;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class LokiClonePickupMixin {
    @Inject(method="onPlayerCollision",at=@At("HEAD"),cancellable=true)
    private void gexpress$cloneCannotCollectItems(PlayerEntity player,CallbackInfo ci){if(player instanceof ServerPlayerEntity p&&LokiManager.isControllingClone(p))ci.cancel();}
}
