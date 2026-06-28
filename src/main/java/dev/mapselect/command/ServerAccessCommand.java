package dev.mapselect.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.server.ServerAccessManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ServerAccessCommand {
	private ServerAccessCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> openTree() {
		return CommandManager.literal("open")
			.requires(source -> GexpressPermissions.canManageServerAccess(source)
				|| GexpressPermissions.canUseCommandPath(source, "open"))
			.executes(ctx -> setOpen(ctx.getSource(), true));
	}

	public static LiteralArgumentBuilder<ServerCommandSource> closeTree() {
		return CommandManager.literal("close")
			.requires(source -> GexpressPermissions.canManageServerAccess(source)
				|| GexpressPermissions.canUseCommandPath(source, "close"))
			.executes(ctx -> setOpen(ctx.getSource(), false));
	}

	private static int setOpen(ServerCommandSource source, boolean open) {
		boolean changed = ServerAccessManager.setOpen(source.getServer(), open);
		if (open) {
			Text message = Text.literal(changed
				? "Server opened. Everyone can join again."
				: "Server is already open.").formatted(Formatting.GREEN);
			source.sendMessage(message);
			return 1;
		}

		int kicked = ServerAccessManager.kickBelowHost(source.getServer());
		Text message = Text.literal(changed
			? "Server closed. Kicked " + kicked + " player(s) below Host."
			: "Server was already closed. Kicked " + kicked + " player(s) below Host.").formatted(Formatting.YELLOW);
		source.sendMessage(message);
		return 1;
	}
}
