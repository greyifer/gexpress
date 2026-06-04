package dev.mapselect.command.roles;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mapselect.role.pelican.PelicanManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class PelicanCommand {
	private PelicanCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("pelican")
			.then(CommandManager.literal("leave")
				.executes(PelicanCommand::runLeave));
	}

	private static int runLeave(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		if (!PelicanManager.releaseFromBelly(player, true)) {
			ctx.getSource().sendError(Text.literal("You are not inside a Pelican."));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Text.literal("You climbed out of the Pelican.")
			.formatted(Formatting.YELLOW), false);
		return 1;
	}

}
