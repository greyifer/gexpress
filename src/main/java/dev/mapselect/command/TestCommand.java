package dev.mapselect.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.mapselect.MapSelect;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.task.ConversationTask;
import dev.mapselect.role.warlock.WarlockComponent;
import dev.mapselect.testing.GexpressTestState;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.harpymodloader.commands.argument.ModifierArgumentType;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class TestCommand {
	private static final Predicate<ServerCommandSource> OP = GexpressPermissions::canUseAdminCommands;

	private TestCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return buildRoleTestTree();
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildRoleTestTree() {
		return CommandManager.literal("test")
			.requires(OP)
			.then(CommandManager.literal("role")
				.then(CommandManager.literal("clear")
					.executes(ctx -> runClearRole(ctx, self(ctx)))
					.then(CommandManager.argument("players", EntityArgumentType.players())
						.executes(ctx -> runClearRole(ctx, EntityArgumentType.getPlayers(ctx, "players")))))
				.then(CommandManager.argument("role", RoleArgumentType.create())
					.executes(ctx -> runSetRole(ctx, RoleArgumentType.getRole(ctx, "role"), self(ctx)))
					.then(CommandManager.argument("players", EntityArgumentType.players())
						.executes(ctx -> runSetRole(ctx, RoleArgumentType.getRole(ctx, "role"),
							EntityArgumentType.getPlayers(ctx, "players"))))));
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildModifierTestTree() {
		return CommandManager.literal("test")
			.requires(OP)
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.then(CommandManager.argument("modifier", ModifierArgumentType.create())
						.suggests(suggestAddModifiers("players"))
						.executes(ctx -> runAddModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"),
							EntityArgumentType.getPlayers(ctx, "players")))))
				.then(CommandManager.argument("modifier", ModifierArgumentType.create())
					.suggests(suggestAddModifiers(null))
					.executes(ctx -> runAddModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"), self(ctx)))
					.then(CommandManager.argument("players", EntityArgumentType.players())
						.executes(ctx -> runAddModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"),
							EntityArgumentType.getPlayers(ctx, "players"))))))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.then(CommandManager.argument("modifier", ModifierArgumentType.create())
						.suggests(suggestRemoveModifiers("players"))
						.executes(ctx -> runRemoveModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"),
							EntityArgumentType.getPlayers(ctx, "players")))))
				.then(CommandManager.argument("modifier", ModifierArgumentType.create())
					.suggests(suggestRemoveModifiers(null))
					.executes(ctx -> runRemoveModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"), self(ctx)))
					.then(CommandManager.argument("players", EntityArgumentType.players())
						.executes(ctx -> runRemoveModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"),
							EntityArgumentType.getPlayers(ctx, "players"))))))
			.then(CommandManager.literal("clear")
				.executes(ctx -> runClearModifiers(ctx, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runClearModifiers(ctx, EntityArgumentType.getPlayers(ctx, "players")))));
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTaskTestTree() {
		return CommandManager.literal("test")
			.requires(OP)
			.then(CommandManager.literal("clear")
				.executes(ctx -> runClearTasks(ctx, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runClearTasks(ctx, EntityArgumentType.getPlayers(ctx, "players")))))
			.then(CommandManager.argument("task", StringArgumentType.word())
				.suggests(TestCommand::suggestTasks)
				.executes(ctx -> runGiveTask(ctx, StringArgumentType.getString(ctx, "task"), self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runGiveTask(ctx, StringArgumentType.getString(ctx, "task"),
						EntityArgumentType.getPlayers(ctx, "players")))));
	}

	private static Collection<ServerPlayerEntity> self(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		return List.of(ctx.getSource().getPlayerOrThrow());
	}

	private static SuggestionProvider<ServerCommandSource> suggestAddModifiers(String playersArg) {
		return (ctx, builder) -> {
			WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(ctx.getSource().getWorld());
			if (mods == null) return builder.buildFuture();
			Collection<ServerPlayerEntity> targets = suggestedTargets(ctx, playersArg);
			String remaining = builder.getRemainingLowerCase();
			HMLModifiers.MODIFIERS.stream()
				.sorted(Comparator.comparing(m -> m.identifier().toString()))
				.filter(modifier -> targets.isEmpty() || targets.stream().anyMatch(player -> !hasModifier(mods, player, modifier)))
				.map(modifier -> modifier.identifier().toString())
				.filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
				.forEach(builder::suggest);
			return builder.buildFuture();
		};
	}

	private static SuggestionProvider<ServerCommandSource> suggestRemoveModifiers(String playersArg) {
		return (ctx, builder) -> {
			WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(ctx.getSource().getWorld());
			if (mods == null) return builder.buildFuture();
			Collection<ServerPlayerEntity> targets = suggestedTargets(ctx, playersArg);
			String remaining = builder.getRemainingLowerCase();
			HMLModifiers.MODIFIERS.stream()
				.sorted(Comparator.comparing(m -> m.identifier().toString()))
				.filter(modifier -> !targets.isEmpty() && targets.stream().anyMatch(player -> hasModifier(mods, player, modifier)))
				.map(modifier -> modifier.identifier().toString())
				.filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
				.forEach(builder::suggest);
			return builder.buildFuture();
		};
	}

	private static Collection<ServerPlayerEntity> suggestedTargets(CommandContext<ServerCommandSource> ctx, String playersArg) {
		try {
			if (playersArg != null) return EntityArgumentType.getPlayers(ctx, playersArg);
			ServerPlayerEntity player = ctx.getSource().getPlayer();
			return player == null ? List.of() : List.of(player);
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private static boolean hasModifier(WorldModifierComponent mods, ServerPlayerEntity player, Modifier modifier) {
		return mods.getModifiers(player.getUuid()).stream()
			.anyMatch(current -> current == modifier || current.identifier().equals(modifier.identifier()));
	}

	private static CompletableFuture<Suggestions> suggestTasks(CommandContext<ServerCommandSource> ctx,
			SuggestionsBuilder builder) {
		String remaining = builder.getRemainingLowerCase();
		for (String task : List.of("outside", "sleep", "eat", "drink", "conversation")) {
			if (task.startsWith(remaining)) builder.suggest(task);
		}
		return builder.buildFuture();
	}

	private static int runSetRole(CommandContext<ServerCommandSource> ctx, Role role,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(src.getWorld());
		if (game == null) {
			src.sendError(Text.literal("WATHE game component is not available in this world."));
			return 0;
		}

		for (ServerPlayerEntity player : players) {
			clearWarlockState(src, player);
			game.addRole(player, role);
			GexpressTestState.markRole(player.getUuid());
		}
		game.sync();

		String roleName = id(role);
		sendApplied(src, "role", roleName, players);
		return players.size();
	}

	private static int runClearRole(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(src.getWorld());
		if (game == null) {
			src.sendError(Text.literal("WATHE game component is not available in this world."));
			return 0;
		}

		for (ServerPlayerEntity player : players) {
			game.getRoles().remove(player.getUuid());
			GexpressTestState.unmarkRole(player.getUuid());
			clearWarlockState(src, player);
		}
		game.sync();

		src.sendFeedback(() -> Text.literal("Cleared test role for " + playerList(players) + ".")
			.formatted(Formatting.YELLOW), true);
		return players.size();
	}

	private static int runAddModifier(CommandContext<ServerCommandSource> ctx, Modifier modifier,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(src.getWorld());
		if (mods == null) {
			src.sendError(Text.literal("HML modifier component is not available in this world."));
			return 0;
		}

		for (ServerPlayerEntity player : players) {
			UUID id = player.getUuid();
			if (!mods.isModifier(id, modifier)) {
				mods.addModifier(id, modifier);
				safeAssignModifier(player, modifier);
			}
			GexpressTestState.markModifier(id, modifier);
		}
		mods.sync();

		sendApplied(src, "modifier", id(modifier), players);
		return players.size();
	}

	private static int runRemoveModifier(CommandContext<ServerCommandSource> ctx, Modifier modifier,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(src.getWorld());
		if (mods == null) {
			src.sendError(Text.literal("HML modifier component is not available in this world."));
			return 0;
		}

		for (ServerPlayerEntity player : players) {
			List<Modifier> current = mods.getModifiers(player.getUuid());
			boolean removed = current.removeIf(m -> m == modifier || m.identifier().equals(modifier.identifier()));
			if (removed) {
				safeRemoveModifier(player, modifier);
			}
			GexpressTestState.unmarkModifier(player.getUuid(), modifier);
		}
		mods.sync();

		src.sendFeedback(() -> Text.literal("Removed test modifier " + id(modifier) + " from " + playerList(players) + ".")
			.formatted(Formatting.YELLOW), true);
		return players.size();
	}

	private static int runClearModifiers(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(src.getWorld());
		if (mods == null) {
			src.sendError(Text.literal("HML modifier component is not available in this world."));
			return 0;
		}

		for (ServerPlayerEntity player : players) {
			ArrayList<Modifier> current = mods.getModifiers(player.getUuid());
			List<Modifier> removed = new ArrayList<>(current);
			current.clear();
			for (Modifier modifier : removed) {
				safeRemoveModifier(player, modifier);
			}
			GexpressTestState.clearModifiers(player.getUuid());
		}
		mods.sync();

		src.sendFeedback(() -> Text.literal("Cleared test modifiers for " + playerList(players) + ".")
			.formatted(Formatting.YELLOW), true);
		return players.size();
	}

	private static int runGiveTask(CommandContext<ServerCommandSource> ctx, String rawTask,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		PlayerMoodComponent.TrainTask forcedTask = newTask(rawTask);
		if (forcedTask == null) {
			src.sendError(Text.literal("Unknown task '" + rawTask + "'. Use outside, sleep, eat, drink, or conversation."));
			return 0;
		}

		for (ServerPlayerEntity player : players) {
			PlayerMoodComponent mood = PlayerMoodComponent.KEY.getNullable(player);
			if (mood == null) continue;
			mood.tasks.clear();
			mood.tasks.put(forcedTask.getType(), newTask(rawTask));
			mood.sync();
		}

		src.sendFeedback(() -> Text.literal("Forced task " + taskName(forcedTask) + " for " + playerList(players) + ".")
			.formatted(Formatting.GREEN), true);
		return players.size();
	}

	private static int runClearTasks(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		for (ServerPlayerEntity player : players) {
			PlayerMoodComponent mood = PlayerMoodComponent.KEY.getNullable(player);
			if (mood == null) continue;
			mood.tasks.clear();
			mood.sync();
		}

		src.sendFeedback(() -> Text.literal("Cleared tasks for " + playerList(players) + ".")
			.formatted(Formatting.YELLOW), true);
		return players.size();
	}

	private static PlayerMoodComponent.Task parseTask(String rawTask) {
		if (rawTask == null) return null;
		return switch (rawTask.toLowerCase(Locale.ROOT)) {
			case "outside", "freshair", "fresh_air", "air" -> PlayerMoodComponent.Task.OUTSIDE;
			case "sleep", "bed" -> PlayerMoodComponent.Task.SLEEP;
			case "eat", "food" -> PlayerMoodComponent.Task.EAT;
			case "drink", "cocktail" -> PlayerMoodComponent.Task.DRINK;
			default -> null;
		};
	}

	private static PlayerMoodComponent.TrainTask newTask(String rawTask) {
		if (rawTask != null && switch (rawTask.toLowerCase(Locale.ROOT)) {
			case "conversation", "smalltalk", "small_talk", "talk" -> true;
			default -> false;
		}) {
			return ConversationTask.createConfigured();
		}
		PlayerMoodComponent.Task task = parseTask(rawTask);
		return task == null ? null : newTask(task);
	}

	private static PlayerMoodComponent.TrainTask newTask(PlayerMoodComponent.Task task) {
		return switch (task) {
			case OUTSIDE -> new PlayerMoodComponent.OutsideTask(GameConstants.OUTSIDE_TASK_DURATION);
			case SLEEP -> new PlayerMoodComponent.SleepTask(GameConstants.SLEEP_TASK_DURATION);
			case EAT -> new PlayerMoodComponent.EatTask();
			case DRINK -> new PlayerMoodComponent.DrinkTask();
		};
	}

	private static String taskName(PlayerMoodComponent.Task task) {
		return task == null ? "(none)" : task.name().toLowerCase(Locale.ROOT);
	}

	private static String taskName(PlayerMoodComponent.TrainTask task) {
		if (ConversationTask.isConversation(task)) return "conversation";
		return task == null ? "(none)" : taskName(task.getType());
	}

	private static void sendApplied(ServerCommandSource src, String type, String value,
			Collection<ServerPlayerEntity> players) {
		src.sendFeedback(() -> Text.literal("Applied test " + type + " " + value + " to " + playerList(players) + ".")
			.formatted(Formatting.GREEN), true);
	}

	private static void clearWarlockState(ServerCommandSource src, ServerPlayerEntity player) {
		WarlockComponent comp = WarlockComponent.KEY.getNullable(src.getWorld());
		if (comp != null) {
			comp.removeWarlock(player.getUuid());
		}
	}

	private static void safeAssignModifier(ServerPlayerEntity player, Modifier modifier) {
		try {
			ModifierAssigned.EVENT.invoker().assignModifier(player, modifier);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierAssigned listener failed during /g modifiers test add for {} on {}.",
				id(modifier), player.getName().getString(), t);
		}
	}

	private static void safeRemoveModifier(ServerPlayerEntity player, Modifier modifier) {
		try {
			ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
			player.calculateDimensions();
			player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierRemoved listener failed during /g modifiers test remove for {} on {}.",
				id(modifier), player.getName().getString(), t);
		}
	}

	private static String id(Role role) {
		return role == null ? "(none)" : role.identifier().toString();
	}

	private static String id(Modifier modifier) {
		return modifier == null ? "(none)" : modifier.identifier().toString();
	}

	private static String playerList(Collection<ServerPlayerEntity> players) {
		List<String> names = new ArrayList<>();
		for (ServerPlayerEntity player : players) {
			names.add(player.getName().getString());
		}
		return String.join(", ", names);
	}
}
