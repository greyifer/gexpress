package dev.mapselect.command.roles;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.role.vulture.VultureManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

public final class PelicanCommand {
	private PelicanCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("pelican")
			.then(CommandManager.literal("leave")
				.executes(PelicanCommand::runLeave))
			.then(CommandManager.literal("release")
				.requires(GexpressPermissions::canUseRoleCommands)
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runRelease(ctx, EntityArgumentType.getPlayers(ctx, "players")))));
	}

	private static int runLeave(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		if (!VultureManager.releaseFromBelly(player, true)) {
			ctx.getSource().sendError(Text.literal("You are not inside a Pelican."));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Text.literal("You climbed out of the Pelican.")
			.formatted(Formatting.YELLOW), false);
		return 1;
	}

	private static int runRelease(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		int released = 0;
		for (ServerPlayerEntity player : players) {
			if (VultureManager.releaseFromBelly(player, true)) released++;
		}
		int count = released;
		ctx.getSource().sendFeedback(() -> Text.literal("Released " + count + " player(s) from Pelicans.")
			.formatted(count > 0 ? Formatting.GREEN : Formatting.GRAY), true);
		return released;
	}
}
