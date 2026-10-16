package dev.mapselect.command.admin;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.mapselect.command.GexpressFeedback;
import dev.mapselect.leaderboard.LeaderboardManager;
import dev.mapselect.leaderboard.LeaderboardStat;
import dev.mapselect.leaderboard.LeaderboardStatsComponent;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

public final class LeaderboardCommand {
	private static final SuggestionProvider<ServerCommandSource> STATS = (context, builder) ->
		CommandSource.suggestMatching(LeaderboardStat.ids(), builder);

	private LeaderboardCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("leaderboard")
			.requires(source -> GexpressPermissions.canUseAdminCommands(source)
				|| GexpressPermissions.canUseCommandBranch(source, "admin", "leaderboard"))
			.then(CommandManager.literal("get")
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(ctx -> runGet(ctx,
						GameProfileArgumentType.getProfileArgument(ctx, "players")))))
			.then(changeBranch("set", Change.SET))
			.then(changeBranch("add", Change.ADD))
			.then(changeBranch("remove", Change.REMOVE))
			.then(CommandManager.literal("reset")
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(ctx -> runReset(ctx,
						GameProfileArgumentType.getProfileArgument(ctx, "players")))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> changeBranch(String literal, Change change) {
		return CommandManager.literal(literal)
			.requires(source -> GexpressPermissions.canUseAdminCommands(source)
				|| GexpressPermissions.canUseCommandPath(source, "admin", "leaderboard", literal))
			.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
				.then(CommandManager.argument("stat", StringArgumentType.word())
					.suggests(STATS)
					.then(CommandManager.argument("value", LongArgumentType.longArg(0L))
						.executes(ctx -> runChange(ctx,
							GameProfileArgumentType.getProfileArgument(ctx, "players"),
							StringArgumentType.getString(ctx, "stat"),
							LongArgumentType.getLong(ctx, "value"), change)))));
	}

	private static int runGet(CommandContext<ServerCommandSource> ctx, Collection<GameProfile> profiles) {
		int shown = 0;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			LeaderboardStatsComponent.AdminSnapshot stats = LeaderboardManager.adminSnapshot(
				ctx.getSource().getServer(), profile);
			String name = stats.name().isBlank() ? profile.getName() : stats.name();
			GexpressFeedback.send(ctx.getSource(), () -> Text.literal(name + ": "
				+ "kills=" + stats.kills() + ", wins=" + stats.wins()
				+ ", killer_wins=" + stats.killerWins() + ", neutral_wins=" + stats.neutralWins()
				+ ", civilian_wins=" + stats.civilianWins()
				+ ", current_win_streak=" + stats.currentWinStreak()
				+ ", longest_win_streak=" + stats.longestWinStreak()
				+ ", longest_alive_seconds=" + stats.longestAliveSeconds()).formatted(Formatting.AQUA), false);
			shown++;
		}
		return shown;
	}

	private static int runChange(CommandContext<ServerCommandSource> ctx, Collection<GameProfile> profiles,
			String statId, long amount, Change change) {
		LeaderboardStat stat = LeaderboardStat.byId(statId);
		if (stat == null) {
			ctx.getSource().sendError(Text.literal("Unknown leaderboard stat '" + statId
				+ "'. Use: " + String.join(", ", LeaderboardStat.ids())));
			return 0;
		}
		int changed = 0;
		long lastValue = 0L;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			lastValue = switch (change) {
				case SET -> LeaderboardManager.adminSet(ctx.getSource().getServer(), profile, stat, amount);
				case ADD -> LeaderboardManager.adminAdd(ctx.getSource().getServer(), profile, stat, amount);
				case REMOVE -> LeaderboardManager.adminAdd(ctx.getSource().getServer(), profile, stat, -amount);
			};
			changed++;
		}
		int count = changed;
		long result = lastValue;
		GexpressFeedback.send(ctx.getSource(), () -> Text.literal(change.label + " " + stat.id()
			+ " for " + count + " player(s); last resulting value: " + result + ".")
			.formatted(Formatting.GREEN), true);
		return changed;
	}

	private static int runReset(CommandContext<ServerCommandSource> ctx, Collection<GameProfile> profiles) {
		int reset = 0;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			LeaderboardManager.adminReset(ctx.getSource().getServer(), profile);
			reset++;
		}
		int count = reset;
		GexpressFeedback.send(ctx.getSource(), () -> Text.literal(
			"Reset all leaderboard stats for " + count + " player(s).").formatted(Formatting.GREEN), true);
		return reset;
	}

	private enum Change {
		SET("Set"),
		ADD("Added to"),
		REMOVE("Removed from");

		private final String label;

		Change(String label) {
			this.label = label;
		}
	}
}
