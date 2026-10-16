package dev.mapselect.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mapselect.replay.ReplayManager;
import net.minecraft.server.command.*;

public final class ReplayCommand {
    private ReplayCommand() {}
    public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
        return CommandManager.literal("replay").requires(source -> source.hasPermissionLevel(2)).executes(context->{ReplayManager.catalog(context.getSource().getPlayerOrThrow());return 1;})
            .then(CommandManager.literal("on").executes(c->{if(ReplayManager.isDeleting()){c.getSource().sendError(net.minecraft.text.Text.literal("Wait for replay deletion to finish."));return 0;}ReplayManager.setEnabled(c.getSource().getServer(),true);c.getSource().sendFeedback(()->net.minecraft.text.Text.literal("Replays enabled."),true);return 1;}))
            .then(CommandManager.literal("off").executes(c->{var source=c.getSource();source.sendFeedback(()->net.minecraft.text.Text.literal("Replays disabled. Deleting saved replays..."),true);
                ReplayManager.setEnabled(source.getServer(),false).whenComplete((unused,error)->source.getServer().execute(()->{
                    if(error==null)source.sendFeedback(()->net.minecraft.text.Text.literal("All saved replays deleted."),true);
                    else source.sendError(net.minecraft.text.Text.literal("Replays are disabled, but deletion failed. Check the server log."));
                }));return 1;}))
            .then(CommandManager.literal("status").requires(dev.mapselect.permissions.GexpressPermissions::canUseAdminCommands)
                .executes(context -> { context.getSource().sendFeedback(() -> net.minecraft.text.Text.literal("Replays: "+(dev.mapselect.config.GexpressConfig.replayEnabled?"enabled":"disabled")+". "+dev.mapselect.replay.ReplayRecorder.status(context.getSource().getWorld())), false); return 1; }));
    }
}
