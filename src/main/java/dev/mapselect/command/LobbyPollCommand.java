package dev.mapselect.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mapselect.poll.LobbyPollManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public final class LobbyPollCommand {
	private LobbyPollCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("poll")
			.executes(ctx -> LobbyPollManager.openBuilder(ctx.getSource()))
			.then(CommandManager.literal("yes")
				.executes(ctx -> LobbyPollManager.voteCommand(ctx.getSource(), true)))
			.then(CommandManager.literal("no")
				.executes(ctx -> LobbyPollManager.voteCommand(ctx.getSource(), false)));
	}
}
