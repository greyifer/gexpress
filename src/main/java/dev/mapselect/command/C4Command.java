package dev.mapselect.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.role.bombspecialist.C4BackComponent;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

public class C4Command {

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("c4")
			.requires(GexpressPermissions::canUseAdminCommands)
			.then(CommandManager.literal("attach")
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(C4Command::runAttach)))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(C4Command::runRemove)))
			.then(CommandManager.literal("clear")
				.executes(C4Command::runClear));
	}

	private static C4BackComponent component(ServerCommandSource src) {
		return C4BackComponent.KEY.get(src.getWorld());
	}

	private static int runAttach(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		C4BackComponent comp = component(src);
		Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int changed = 0;
		for (com.mojang.authlib.GameProfile p : profiles) {
			if (comp.addC4(p.getId())) changed++;
		}
		final int f = changed;
		src.sendFeedback(() -> Text.literal("Attached C4 to " + f + " player(s).").formatted(Formatting.GREEN), true);
		return changed;
	}

	private static int runRemove(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		C4BackComponent comp = component(src);
		Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int changed = 0;
		for (com.mojang.authlib.GameProfile p : profiles) {
			if (comp.removeC4(p.getId())) changed++;
		}
		final int f = changed;
		src.sendFeedback(() -> Text.literal("Removed C4 from " + f + " player(s).").formatted(Formatting.YELLOW), true);
		return changed;
	}

	private static int runClear(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		C4BackComponent comp = component(src);
		int count = comp.getCarriers().size();
		if (comp.clearAll()) {
			src.sendFeedback(() -> Text.literal("Cleared C4 from " + count + " player(s).").formatted(Formatting.YELLOW), true);
		} else {
			src.sendFeedback(() -> Text.literal("No C4 attached to anyone.").formatted(Formatting.GRAY), false);
		}
		return count;
	}
}
