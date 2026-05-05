package dev.mapselect.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mapselect.host.TrustedComponent;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.UUID;

public class TrustedCommand {
	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("trusted")
			.requires(GexpressPermissions::canUseAdminCommands)
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(TrustedCommand::runAdd)))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(TrustedCommand::runRemove)))
			.then(CommandManager.literal("list")
				.executes(TrustedCommand::runList));
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
		for (GameProfile profile : profiles) {
			if (comp.addTrusted(profile.getId())) {
				added++;
				ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(profile.getId());
				if (online != null) {
					online.sendMessage(Text.literal("You are now ").formatted(Formatting.GRAY)
						.append(GexpressPermissions.trustedBadge()), false);
					TagCommand.refreshPlayerListName(online);
				}
			}
		}
		final int f = added;
		src.sendFeedback(() -> Text.literal("Added " + f + " trusted player(s).").formatted(Formatting.GREEN), true);
		return added;
	}

	private static int runRemove(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		TrustedComponent comp = component(src);
		Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int removed = 0;
		for (GameProfile profile : profiles) {
			if (comp.removeTrusted(profile.getId())) {
				removed++;
				ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(profile.getId());
				if (online != null) {
					online.sendMessage(Text.literal("You are no longer Trusted.").formatted(Formatting.GRAY), false);
					TagCommand.refreshPlayerListName(online);
				}
			}
		}
		final int f = removed;
		src.sendFeedback(() -> Text.literal("Removed " + f + " trusted player(s).").formatted(Formatting.YELLOW), true);
		return removed;
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		TrustedComponent comp = component(src);
		if (comp.getTrusted().isEmpty()) {
			src.sendFeedback(() -> Text.literal("No trusted players.").formatted(Formatting.GRAY), false);
			return 0;
		}
		StringBuilder sb = new StringBuilder();
		for (UUID uuid : comp.getTrusted()) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(nameFor(src, uuid));
		}
		String names = sb.toString();
		src.sendFeedback(() -> Text.literal("Trusted (" + comp.getTrusted().size() + "): " + names), false);
		return comp.getTrusted().size();
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
}
