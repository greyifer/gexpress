package dev.mapselect.command.admin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mapselect.command.CommandPlayerFormat;
import dev.mapselect.host.HostComponent;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

public class HostCommand {

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("host")
			.requires(source -> GexpressPermissions.canUseAdminCommands(source)
				|| GexpressPermissions.canUseCommandBranch(source, "admin", "host"))
			.then(CommandManager.literal("add")
				.requires(source -> canUseHostCommand(source, "add"))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(HostCommand::runAdd)))
			.then(CommandManager.literal("remove")
				.requires(source -> canUseHostCommand(source, "remove"))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(HostCommand::runRemove)))
			.then(CommandManager.literal("list")
				.requires(source -> canUseHostCommand(source, "list"))
				.executes(HostCommand::runList));
	}

	private static boolean canUseHostCommand(ServerCommandSource source, String subcommand) {
		return GexpressPermissions.canUseAdminCommands(source)
			|| GexpressPermissions.canUseCommandPath(source, "admin", "host", subcommand);
	}

	private static HostComponent component(ServerCommandSource src) {
		return HostComponent.KEY.get(src.getWorld());
	}

	private static int runAdd(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		HostComponent comp = component(src);
		Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int added = 0;
		for (com.mojang.authlib.GameProfile p : CommandPlayerFormat.validProfiles(profiles)) {
			if (comp.addHost(p.getId())) {
				added++;
				ServerPlayerEntity online = CommandPlayerFormat.online(src, p);
				if (online != null) {
					online.sendMessage(Text.literal("You are now a ").formatted(Formatting.GRAY)
						.append(hostBadge()), false);
					TagCommand.refreshPlayerListName(online);
				}
			}
		}
		final int f = added;
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Added " + f + " host(s).").formatted(Formatting.GREEN), true);
		return added;
	}

	private static int runRemove(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		HostComponent comp = component(src);
		Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int removed = 0;
		for (com.mojang.authlib.GameProfile p : CommandPlayerFormat.validProfiles(profiles)) {
			if (comp.removeHost(p.getId())) {
				removed++;
				ServerPlayerEntity online = CommandPlayerFormat.online(src, p);
				if (online != null) {
					online.sendMessage(Text.literal("You are no longer a Host.").formatted(Formatting.GRAY), false);
					TagCommand.refreshPlayerListName(online);
				}
			}
		}
		final int f = removed;
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Removed " + f + " host(s).").formatted(Formatting.YELLOW), true);
		return removed;
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		HostComponent comp = component(src);
		if (comp.getHosts().isEmpty()) {
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("No hosts.").formatted(Formatting.GRAY), false);
			return 0;
		}
		final String names = CommandPlayerFormat.cachedNames(src, comp.getHosts());
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Hosts (" + comp.getHosts().size() + "): " + names), false);
		return comp.getHosts().size();
	}

	public static Text hostBadge() {
		return GexpressPermissions.hostBadge();
	}
}
