package dev.mapselect.command.admin;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mapselect.command.CommandPlayerFormat;
import dev.mapselect.host.TrustedComponent;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

public class TrustedCommand {
	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("trusted")
			.requires(source -> GexpressPermissions.canUseAdminCommands(source)
				|| GexpressPermissions.canUseCommandBranch(source, "admin", "trusted"))
			.then(CommandManager.literal("add")
				.requires(source -> canUseTrustedCommand(source, "add"))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(TrustedCommand::runAdd)))
			.then(CommandManager.literal("remove")
				.requires(source -> canUseTrustedCommand(source, "remove"))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(TrustedCommand::runRemove)))
			.then(CommandManager.literal("list")
				.requires(source -> canUseTrustedCommand(source, "list"))
				.executes(TrustedCommand::runList));
	}

	private static boolean canUseTrustedCommand(ServerCommandSource source, String subcommand) {
		return GexpressPermissions.canUseAdminCommands(source)
			|| GexpressPermissions.canUseCommandPath(source, "admin", "trusted", subcommand);
	}

	private static TrustedComponent component(ServerCommandSource src) {
		return TrustedComponent.KEY.get(src.getWorld());
	}

	private static int runAdd(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		TrustedComponent comp = component(src);
		Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int added = 0;
		for (GameProfile profile : CommandPlayerFormat.validProfiles(profiles)) {
			if (comp.addTrusted(profile.getId())) {
				added++;
				ServerPlayerEntity online = CommandPlayerFormat.online(src, profile);
				if (online != null) {
					online.sendMessage(Text.literal("You are now ").formatted(Formatting.GRAY)
						.append(GexpressPermissions.trustedBadge()), false);
					TagCommand.refreshPlayerListName(online);
				}
			}
		}
		final int f = added;
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Added " + f + " trusted player(s).").formatted(Formatting.GREEN), true);
		return added;
	}

	private static int runRemove(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		TrustedComponent comp = component(src);
		Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int removed = 0;
		for (GameProfile profile : CommandPlayerFormat.validProfiles(profiles)) {
			if (comp.removeTrusted(profile.getId())) {
				removed++;
				ServerPlayerEntity online = CommandPlayerFormat.online(src, profile);
				if (online != null) {
					online.sendMessage(Text.literal("You are no longer Trusted.").formatted(Formatting.GRAY), false);
					TagCommand.refreshPlayerListName(online);
				}
			}
		}
		final int f = removed;
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Removed " + f + " trusted player(s).").formatted(Formatting.YELLOW), true);
		return removed;
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		TrustedComponent comp = component(src);
		if (comp.getTrusted().isEmpty()) {
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("No trusted players.").formatted(Formatting.GRAY), false);
			return 0;
		}
		String names = CommandPlayerFormat.cachedNames(src, comp.getTrusted());
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Trusted (" + comp.getTrusted().size() + "): " + names), false);
		return comp.getTrusted().size();
	}
}
