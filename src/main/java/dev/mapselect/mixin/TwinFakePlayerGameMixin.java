package dev.mapselect.mixin;

import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.entity.TwinFakePlayerEntity;
import dev.mapselect.role.twins.TwinsManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=GameFunctions.class,remap=false,priority=2000)
public abstract class TwinFakePlayerGameMixin {
    @Inject(method="isPlayerAliveAndSurvival",at=@At("HEAD"),cancellable=true,remap=false)
    private static void gexpress$excludeBodyFromRoundCounts(PlayerEntity player,CallbackInfoReturnable<Boolean> cir){if(player instanceof TwinFakePlayerEntity)cir.setReturnValue(false);}
    @Inject(method="killPlayer(Lnet/minecraft/class_1657;ZLnet/minecraft/class_1657;Lnet/minecraft/class_2960;)V",at=@At("HEAD"),cancellable=true,remap=false)
    private static void gexpress$killNativeTwin(PlayerEntity victim,boolean body,PlayerEntity killer,Identifier reason,CallbackInfo ci){if(victim instanceof TwinFakePlayerEntity twin){TwinsManager.killBody(twin,killer);ci.cancel();}}
}
