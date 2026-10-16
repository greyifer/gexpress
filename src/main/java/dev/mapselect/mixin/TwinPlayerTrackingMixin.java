package dev.mapselect.mixin;

import dev.mapselect.entity.TwinFakePlayerEntity;
import dev.mapselect.role.twins.TwinsManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityTrackerEntry.class)
public abstract class TwinPlayerTrackingMixin {
    @Shadow @Final private Entity entity;
    @Inject(method="startTracking",at=@At("HEAD"))
    private void gexpress$profileBeforeSpawn(ServerPlayerEntity viewer,CallbackInfo ci) {
        if(entity instanceof TwinFakePlayerEntity body) TwinsManager.sendBody(viewer,body,true);
    }
}
