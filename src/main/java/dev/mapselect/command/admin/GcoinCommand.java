package dev.mapselect.command.admin;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mapselect.currency.GcoinComponent;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

public final class GcoinCommand {
	private GcoinCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("gcoin")
			.requires(source -> GexpressPermissions.canManageEconomy(source)
				|| GexpressPermissions.canUseCommandBranch(source, "admin", "gcoin"))
			.then(CommandManager.literal("give")
				.requires(source -> GexpressPermissions.canManageEconomy(source)
					|| GexpressPermissions.canUseCommandPath(source, "admin", "gcoin", "give"))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
						.executes(ctx -> runChange(ctx,
							GameProfileArgumentType.getProfileArgument(ctx, "players"),
							IntegerArgumentType.getInteger(ctx, "amount"),
							Change.GIVE)))))
			.then(CommandManager.literal("remove")
				.requires(source -> GexpressPermissions.canManageEconomy(source)
					|| GexpressPermissions.canUseCommandPath(source, "admin", "gcoin", "remove"))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
						.executes(ctx -> runChange(ctx,
							GameProfileArgumentType.getProfileArgument(ctx, "players"),
							IntegerArgumentType.getInteger(ctx, "amount"),
							Change.REMOVE)))))
			.then(CommandManager.literal("set")
				.requires(source -> GexpressPermissions.canManageEconomy(source)
					|| GexpressPermissions.canUseCommandPath(source, "admin", "gcoin", "set"))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
						.executes(ctx -> runChange(ctx,
							GameProfileArgumentType.getProfileArgument(ctx, "players"),
							IntegerArgumentType.getInteger(ctx, "amount"),
							Change.SET)))));
	}

	private static int runChange(CommandContext<ServerCommandSource> ctx, Collection<GameProfile> profiles,
			int amount, Change change) {
		ServerCommandSource source = ctx.getSource();
		GcoinComponent gcoins = GcoinComponent.KEY.get(source.getWorld());
		int changed = 0;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			boolean updated = switch (change) {
				case GIVE -> gcoins.add(profile.getId(), amount);
				case REMOVE -> gcoins.remove(profile.getId(), amount);
				case SET -> gcoins.setBalance(profile.getId(), amount);
			};
			if (updated) changed++;
		}

		final int finalChanged = changed;
		source.sendFeedback(() -> Text.literal(change.feedback(amount, finalChanged)).formatted(Formatting.GREEN), true);
		return changed;
	}

	private enum Change {
		GIVE,
		REMOVE,
		SET;

		private String feedback(int amount, int players) {
			return switch (this) {
				case GIVE -> "Gave " + amount + " G'Coin to " + players + " player(s).";
				case REMOVE -> "Removed " + amount + " G'Coin from " + players + " player(s).";
				case SET -> "Set G'Coin to " + amount + " for " + players + " player(s).";
			};
		}
	}
}
