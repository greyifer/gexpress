package dev.mapselect.mixin;

import net.minecraft.server.command.MeCommand;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MeCommand.class)
public abstract class InGameMeCommandMixin {
    @Inject(method="method_13238",at=@At("HEAD"),cancellable=true)
    private static void gexpress$blockActionChat(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,CallbackInfoReturnable<Integer> cir) {
        var player=context.getSource().getPlayer();
        if(player!=null && dev.mapselect.game.DeadPlayerStatus.isRunningOrStopping(player.getWorld())) {
            context.getSource().sendError(net.minecraft.text.Text.literal("/me is disabled during a match.")); cir.setReturnValue(0);
        }
    }
}
