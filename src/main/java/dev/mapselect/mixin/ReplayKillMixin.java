package dev.mapselect.mixin;

import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.replay.ReplayRecorder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=GameFunctions.class, remap=false)
public abstract class ReplayKillMixin {
    @Unique private static final ThreadLocal<java.util.ArrayDeque<Boolean>> GEXPRESS_REPLAY_ALIVE=ThreadLocal.withInitial(java.util.ArrayDeque::new);
    @Inject(method="killPlayer(Lnet/minecraft/class_1657;ZLnet/minecraft/class_1657;Lnet/minecraft/class_2960;)V", at=@At("HEAD"), remap=false)
    private static void gexpress$before(PlayerEntity victim, boolean body, PlayerEntity killer, Identifier reason, CallbackInfo ci) {
        GEXPRESS_REPLAY_ALIVE.get().push(GameFunctions.isPlayerAliveAndSurvival(victim));
    }
    @Inject(method="killPlayer(Lnet/minecraft/class_1657;ZLnet/minecraft/class_1657;Lnet/minecraft/class_2960;)V", at=@At("RETURN"), remap=false)
    private static void gexpress$after(PlayerEntity victim, boolean body, PlayerEntity killer, Identifier reason, CallbackInfo ci) {
        var stack=GEXPRESS_REPLAY_ALIVE.get(); boolean alive=!stack.isEmpty() && stack.pop(); if(stack.isEmpty()) GEXPRESS_REPLAY_ALIVE.remove();
        if(alive && !GameFunctions.isPlayerAliveAndSurvival(victim)) ReplayRecorder.death(victim,killer);
    }
}
