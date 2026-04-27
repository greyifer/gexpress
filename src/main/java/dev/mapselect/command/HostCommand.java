package dev.mapselect.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mapselect.host.HostComponent;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.UUID;

public class HostCommand {

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("host")
			.requires(GexpressPermissions::canUseAdminCommands)
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(HostCommand::runAdd)))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(HostCommand::runRemove)))
			.then(CommandManager.literal("list")
				.executes(HostCommand::runList));
	}

	private static HostComponent component(ServerCommandSource src) {
		return HostComponent.KEY.get(src.getWorld());
	}

	private static int runAdd(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		HostComponent comp = component(src);
		Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int added = 0;
		for (com.mojang.authlib.GameProfile p : profiles) {
			if (comp.addHost(p.getId())) {
				added++;
				ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(p.getId());
				if (online != null) {
					online.sendMessage(Text.literal("You are now a ").formatted(Formatting.GRAY)
						.append(hostBadge()), false);
					online.refreshPositionAndAngles(online.getX(), online.getY(), online.getZ(), online.getYaw(), online.getPitch());
				}
			}
		}
		final int f = added;
		src.sendFeedback(() -> Text.literal("Added " + f + " host(s).").formatted(Formatting.GREEN), true);
		return added;
	}

	private static int runRemove(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		HostComponent comp = component(src);
		Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int removed = 0;
		for (com.mojang.authlib.GameProfile p : profiles) {
			if (comp.removeHost(p.getId())) {
				removed++;
				ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(p.getId());
				if (online != null) {
					online.sendMessage(Text.literal("You are no longer a Host.").formatted(Formatting.GRAY), false);
				}
			}
		}
		final int f = removed;
		src.sendFeedback(() -> Text.literal("Removed " + f + " host(s).").formatted(Formatting.YELLOW), true);
		return removed;
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		HostComponent comp = component(src);
		if (comp.getHosts().isEmpty()) {
			src.sendFeedback(() -> Text.literal("No hosts.").formatted(Formatting.GRAY), false);
			return 0;
		}
		StringBuilder sb = new StringBuilder();
		for (UUID u : comp.getHosts()) {
			String name = nameFor(src, u);
			if (sb.length() > 0) sb.append(", ");
			sb.append(name);
		}
		final String names = sb.toString();
		src.sendFeedback(() -> Text.literal("Hosts (" + comp.getHosts().size() + "): " + names), false);
		return comp.getHosts().size();
	}

	private static String nameFor(ServerCommandSource src, UUID uuid) {
		ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(uuid);
		if (online != null) return online.getGameProfile().getName();
		var cache = src.getServer().getUserCache();
		if (cache != null) {
			var profile = cache.getByUuid(uuid).orElse(null);
			if (profile != null && profile.getName() != null) return profile.getName();
		}
		return uuid.toString();
	}

	public static Text hostBadge() {
		return GexpressPermissions.hostBadge();
	}
}
