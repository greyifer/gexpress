package dev.mapselect.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Predicate;

public final class EndCommand {
	private EndCommand() {}

	private static final Predicate<ServerCommandSource> OP_OR_HOST = GexpressPermissions::canUseHostCommands;

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("end")
			.requires(OP_OR_HOST)
			.executes(ctx -> {
				GameFunctions.stopGame(ctx.getSource().getWorld());
				ctx.getSource().sendFeedback(() -> Text.literal("Ended the current G'Express game.").formatted(Formatting.YELLOW), true);
				return 1;
			})
			.then(CommandManager.literal("force")
				.executes(ctx -> {
					GameFunctions.finalizeGame(ctx.getSource().getWorld());
					ctx.getSource().sendFeedback(() -> Text.literal("Force-finalized the current G'Express game.").formatted(Formatting.YELLOW), true);
					return 1;
				}));
	}
}
