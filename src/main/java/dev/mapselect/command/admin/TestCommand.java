package dev.mapselect.command.admin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.mapselect.MapSelect;
import dev.mapselect.game.GexpressGameModes;
import dev.mapselect.network.role.mafia.MafiaIntroPayload;
import dev.mapselect.network.game.TestOverlayPayload;
import dev.mapselect.network.role.timemaster.TimeMasterFreezeStatePayload;
import dev.mapselect.network.role.timemaster.TimeMasterRewindPayload;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.task.ConversationTask;
import dev.mapselect.role.AbilityCooldownReducers;
import dev.mapselect.role.bombspecialist.C4BackComponent;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.role.warlock.WarlockComponent;
import dev.mapselect.testing.GexpressTestState;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class TestCommand {
	private static final Predicate<ServerCommandSource> OP = GexpressPermissions::canUseAdminCommands;
	private static final Predicate<ServerCommandSource> ROLE_TEST = GexpressPermissions::canUseRoleCommands;
	private static final Predicate<ServerCommandSource> MODIFIER_TEST = GexpressPermissions::canUseModifierCommands;
	private static final Map<String, TestPreset> TEST_PRESETS = new LinkedHashMap<>();

	private TestCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("test")
			.requires(src -> ROLE_TEST.test(src) || MODIFIER_TEST.test(src) || OP.test(src))
			.then(roleTestBranch())
			.then(modifierTestBranch())
			.then(taskTestBranch())
			.then(c4TestBranch())
			.then(pelicanTestBranch())
			.then(cooldownTestBranch())
			.then(overlayTestBranch())
			.then(presetTestBranch())
			.then(dev.mapselect.testing.BotTestTools.setupBranch())
			.then(dev.mapselect.testing.BotTestTools.actionBranch())
			.then(dev.mapselect.testing.BotTestTools.reviveBranch())
			.then(dev.mapselect.testing.BotTestTools.stateBranch())
			.then(CommandManager.literal("reset")
				.requires(OP)
				.executes(ctx -> dev.mapselect.testing.BotTestTools.reset(ctx.getSource())
					? 1 : runClearAll(ctx, ctx.getSource().getWorld().getPlayers())));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> roleTestBranch() {
		return CommandManager.literal("role")
			.requires(ROLE_TEST)
			.then(CommandManager.literal("clear")
				.executes(ctx -> runClearRole(ctx, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runClearRole(ctx, EntityArgumentType.getPlayers(ctx, "players")))))
			.then(CommandManager.argument("players", EntityArgumentType.players())
				.then(CommandManager.argument("role", RoleArgumentType.create())
					.executes(ctx -> runSetRole(ctx, RoleArgumentType.getRole(ctx, "role"),
						EntityArgumentType.getPlayers(ctx, "players")))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> modifierTestBranch() {
		return CommandManager.literal("modifier")
			.requires(MODIFIER_TEST)
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.then(CommandManager.argument("modifier", ModifierArgumentType.create())
						.suggests(suggestAddModifiers("players"))
						.executes(ctx -> runAddModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"),
							EntityArgumentType.getPlayers(ctx, "players"))))))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.then(CommandManager.argument("modifier", ModifierArgumentType.create())
						.suggests(suggestRemoveModifiers("players"))
						.executes(ctx -> runRemoveModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"),
							EntityArgumentType.getPlayers(ctx, "players"))))))
			.then(CommandManager.literal("clear")
				.executes(ctx -> runClearModifiers(ctx, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runClearModifiers(ctx, EntityArgumentType.getPlayers(ctx, "players")))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> taskTestBranch() {
		return CommandManager.literal("task")
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

	private static LiteralArgumentBuilder<ServerCommandSource> c4TestBranch() {
		return CommandManager.literal("c4")
			.requires(ROLE_TEST)
			.then(CommandManager.literal("attach")
				.executes(ctx -> runAttachC4(ctx, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runAttachC4(ctx, EntityArgumentType.getPlayers(ctx, "players")))))
			.then(CommandManager.literal("remove")
				.executes(ctx -> runRemoveC4(ctx, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runRemoveC4(ctx, EntityArgumentType.getPlayers(ctx, "players")))))
			.then(CommandManager.literal("clear")
				.executes(TestCommand::runClearC4));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> pelicanTestBranch() {
		return CommandManager.literal("pelican")
			.requires(ROLE_TEST)
			.then(CommandManager.literal("release")
				.executes(ctx -> runReleaseFromPelican(ctx, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runReleaseFromPelican(ctx, EntityArgumentType.getPlayers(ctx, "players")))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> cooldownTestBranch() {
		return CommandManager.literal("cooldown")
			.requires(ROLE_TEST)
			.then(CommandManager.literal("clear")
				.executes(ctx -> runReduceCooldowns(ctx, Integer.MAX_VALUE / 20, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runReduceCooldowns(ctx, Integer.MAX_VALUE / 20,
						EntityArgumentType.getPlayers(ctx, "players")))))
			.then(CommandManager.literal("reduce")
				.then(CommandManager.argument("seconds", IntegerArgumentType.integer(1))
					.executes(ctx -> runReduceCooldowns(ctx, IntegerArgumentType.getInteger(ctx, "seconds"), self(ctx)))
					.then(CommandManager.argument("players", EntityArgumentType.players())
						.executes(ctx -> runReduceCooldowns(ctx, IntegerArgumentType.getInteger(ctx, "seconds"),
							EntityArgumentType.getPlayers(ctx, "players"))))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> overlayTestBranch() {
		return CommandManager.literal("overlay")
			.requires(OP)
			.then(timedOverlayBranch("rewind", 3, TestCommand::runRewindOverlay))
			.then(timedOverlayBranch("freeze", 5, TestCommand::runFreezeOverlay))
			.then(timedOverlayBranch("mafia_intro", 5, TestCommand::runMafiaIntroOverlay))
			.then(timedOverlayBranch("black_white", 8, TestCommand::runBlackWhiteOverlay))
			.then(timedOverlayBranch("cupid", 8, TestCommand::runCupidOverlay))
			.then(CommandManager.literal("clear")
				.executes(ctx -> runClearOverlay(ctx, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runClearOverlay(ctx, EntityArgumentType.getPlayers(ctx, "players")))))
			.then(CommandManager.literal("unfreeze")
				.executes(ctx -> runUnfreezeOverlay(ctx, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runUnfreezeOverlay(ctx, EntityArgumentType.getPlayers(ctx, "players")))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> presetTestBranch() {
		return CommandManager.literal("preset")
			.requires(OP)
			.then(CommandManager.literal("save")
				.then(CommandManager.argument("name", StringArgumentType.word())
					.executes(ctx -> runSavePreset(ctx, StringArgumentType.getString(ctx, "name"),
						ctx.getSource().getPlayerOrThrow()))
					.then(CommandManager.argument("player", EntityArgumentType.player())
						.executes(ctx -> runSavePreset(ctx, StringArgumentType.getString(ctx, "name"),
							EntityArgumentType.getPlayer(ctx, "player"))))))
			.then(CommandManager.literal("apply")
				.then(CommandManager.argument("name", StringArgumentType.word())
					.suggests(TestCommand::suggestPresetNames)
					.executes(ctx -> runApplyPreset(ctx, StringArgumentType.getString(ctx, "name"), self(ctx)))
					.then(CommandManager.argument("players", EntityArgumentType.players())
						.executes(ctx -> runApplyPreset(ctx, StringArgumentType.getString(ctx, "name"),
							EntityArgumentType.getPlayers(ctx, "players"))))))
			.then(CommandManager.literal("delete")
				.then(CommandManager.argument("name", StringArgumentType.word())
					.suggests(TestCommand::suggestPresetNames)
					.executes(ctx -> runDeletePreset(ctx, StringArgumentType.getString(ctx, "name")))))
			.then(CommandManager.literal("clear")
				.executes(TestCommand::runClearPresets))
			.then(CommandManager.literal("list")
				.executes(TestCommand::runListPresets));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> timedOverlayBranch(String name, int defaultSeconds,
			TimedOverlayCommand command) {
		return CommandManager.literal(name)
			.executes(ctx -> command.run(ctx, defaultSeconds, self(ctx)))
			.then(CommandManager.argument("players", EntityArgumentType.players())
				.executes(ctx -> command.run(ctx, defaultSeconds, EntityArgumentType.getPlayers(ctx, "players"))))
			.then(CommandManager.argument("seconds", IntegerArgumentType.integer(1, 600))
				.executes(ctx -> command.run(ctx, IntegerArgumentType.getInteger(ctx, "seconds"), self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> command.run(ctx, IntegerArgumentType.getInteger(ctx, "seconds"),
						EntityArgumentType.getPlayers(ctx, "players")))));
	}

	@FunctionalInterface
	private interface TimedOverlayCommand {
		int run(CommandContext<ServerCommandSource> ctx, int seconds,
				Collection<ServerPlayerEntity> players) throws CommandSyntaxException;
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
		return safeModifiers(mods, player).stream()
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

	private static CompletableFuture<Suggestions> suggestPresetNames(CommandContext<ServerCommandSource> ctx,
			SuggestionsBuilder builder) {
		String remaining = builder.getRemainingLowerCase();
		TEST_PRESETS.keySet().stream()
			.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
			.forEach(builder::suggest);
		return builder.buildFuture();
	}

	private static int runSavePreset(CommandContext<ServerCommandSource> ctx, String rawName,
			ServerPlayerEntity player) {
		ServerCommandSource src = ctx.getSource();
		String name = normalizePresetName(rawName);
		if (name.isEmpty()) {
			src.sendError(Text.literal("Preset name cannot be empty."));
			return 0;
		}
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(player.getWorld());
		List<Identifier> modifierIds = safeModifiers(mods, player).stream()
			.filter(modifier -> modifier != null && modifier.identifier() != null)
			.map(Modifier::identifier)
			.sorted(Comparator.comparing(Identifier::toString))
			.toList();
		TEST_PRESETS.put(name, new TestPreset(role == null ? null : role.identifier(), modifierIds));
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Saved test preset " + name + " from "
			+ player.getName().getString() + ".").formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int runApplyPreset(CommandContext<ServerCommandSource> ctx, String rawName,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		if (denyRoleModifierTestDuringGame(src)) return 0;
		String name = normalizePresetName(rawName);
		TestPreset preset = TEST_PRESETS.get(name);
		if (preset == null) {
			src.sendError(Text.literal("Unknown test preset '" + rawName + "'."));
			return 0;
		}
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(src.getWorld());
		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(src.getWorld());
		if (game == null) {
			src.sendError(Text.literal("WATHE game component is not available in this world."));
			return 0;
		}
		if (mods == null) {
			src.sendError(Text.literal("HML modifier component is not available in this world."));
			return 0;
		}
		Role role = preset.roleId() == null ? null : roleById(preset.roleId());
		if (preset.roleId() != null && role == null) {
			src.sendError(Text.literal("Preset '" + name + "' references missing role " + preset.roleId() + "."));
			return 0;
		}
		List<Modifier> modifiers = new ArrayList<>();
		for (Identifier modifierId : preset.modifierIds()) {
			Modifier modifier = modifierById(modifierId);
			if (modifier == null) {
				src.sendError(Text.literal("Preset '" + name + "' references missing modifier " + modifierId + "."));
				return 0;
			}
			modifiers.add(modifier);
		}

		for (ServerPlayerEntity player : players) {
			applyPresetToPlayer(src, game, mods, role, modifiers, player);
		}
		game.sync();
		mods.sync();
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Applied test preset " + name + " to "
			+ playerList(players) + ".").formatted(Formatting.GREEN), true);
		return players.size();
	}

	private static void applyPresetToPlayer(ServerCommandSource src, GameWorldComponent game,
			WorldModifierComponent mods, Role role, List<Modifier> modifiers, ServerPlayerEntity player) {
		clearWarlockState(src, player);
		if (role == null) {
			game.getRoles().remove(player.getUuid());
			GexpressTestState.unmarkRole(player.getUuid());
		} else {
			game.addRole(player, role);
			GexpressTestState.markRole(player.getUuid());
		}
		ArrayList<Modifier> current = safeModifiers(mods, player);
		List<Modifier> removed = new ArrayList<>(current);
		current.clear();
		for (Modifier modifier : removed) safeRemoveModifier(player, modifier);
		GexpressTestState.clearModifiers(player.getUuid());
		for (Modifier modifier : modifiers) {
			addModifier(mods, player, modifier);
			GexpressTestState.markModifier(player.getUuid(), modifier);
		}
	}

	private static int runDeletePreset(CommandContext<ServerCommandSource> ctx, String rawName) {
		String name = normalizePresetName(rawName);
		TestPreset removed = TEST_PRESETS.remove(name);
		if (removed == null) {
			ctx.getSource().sendError(Text.literal("Unknown test preset '" + rawName + "'."));
			return 0;
		}
		dev.mapselect.command.GexpressFeedback.send(ctx.getSource(), () -> Text.literal("Deleted test preset " + name + ".")
			.formatted(Formatting.YELLOW), true);
		return 1;
	}

	private static int runClearPresets(CommandContext<ServerCommandSource> ctx) {
		int count = TEST_PRESETS.size();
		TEST_PRESETS.clear();
		dev.mapselect.command.GexpressFeedback.send(ctx.getSource(), () -> Text.literal("Cleared " + count + " test preset(s).")
			.formatted(count > 0 ? Formatting.YELLOW : Formatting.GRAY), true);
		return count;
	}

	private static int runListPresets(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (TEST_PRESETS.isEmpty()) {
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("No test presets saved.").formatted(Formatting.GRAY), false);
			return 0;
		}
		for (Map.Entry<String, TestPreset> entry : TEST_PRESETS.entrySet()) {
			TestPreset preset = entry.getValue();
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(entry.getKey() + ": role="
				+ (preset.roleId() == null ? "none" : preset.roleId())
				+ ", modifiers=" + preset.modifierIds()).formatted(Formatting.GRAY), false);
		}
		return TEST_PRESETS.size();
	}

	private static int runSetRole(CommandContext<ServerCommandSource> ctx, Role role,
			Collection<ServerPlayerEntity> players) {
		return setRole(ctx.getSource(), role, players);
	}

	public static int setRole(ServerCommandSource src, Role role, Collection<ServerPlayerEntity> players) {
		if (denyRoleModifierTestDuringGame(src)) return 0;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(src.getWorld());
		if (game == null) {
			src.sendError(Text.literal("WATHE game component is not available in this world."));
			return 0;
		}

		for (ServerPlayerEntity player : players) {
			if (player.getWorld() != src.getWorld()) {
				src.sendError(Text.literal("Test role targets must be in the same world."));
				return 0;
			}
		}
		for (ServerPlayerEntity player : players) {
			Role previous = game.getRole(player);
			if (previous != null && previous != role) {
				org.agmas.harpymodloader.events.ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, previous);
			}
			clearWarlockState(src, player);
			game.addRole(player, role);
			org.agmas.harpymodloader.events.ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
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
		if (denyRoleModifierTestDuringGame(src)) return 0;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(src.getWorld());
		if (game == null) {
			src.sendError(Text.literal("WATHE game component is not available in this world."));
			return 0;
		}

		for (ServerPlayerEntity player : players) {
			Role previous = game.getRole(player);
			if (previous != null) org.agmas.harpymodloader.events.ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, previous);
			game.getRoles().remove(player.getUuid());
			GexpressTestState.unmarkRole(player.getUuid());
			clearWarlockState(src, player);
		}
		game.sync();

		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Cleared test role for " + playerList(players) + ".")
			.formatted(Formatting.YELLOW), true);
		return players.size();
	}

	private static int runAddModifier(CommandContext<ServerCommandSource> ctx, Modifier modifier,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		if (denyRoleModifierTestDuringGame(src)) return 0;
		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(src.getWorld());
		if (mods == null) {
			src.sendError(Text.literal("HML modifier component is not available in this world."));
			return 0;
		}

		for (ServerPlayerEntity player : players) {
			UUID id = player.getUuid();
			if (!mods.isModifier(id, modifier)) {
				addModifier(mods, player, modifier);
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
		if (denyRoleModifierTestDuringGame(src)) return 0;
		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(src.getWorld());
		if (mods == null) {
			src.sendError(Text.literal("HML modifier component is not available in this world."));
			return 0;
		}

		for (ServerPlayerEntity player : players) {
			List<Modifier> current = safeModifiers(mods, player);
			boolean removed = current.removeIf(m -> m == modifier || m.identifier().equals(modifier.identifier()));
			if (removed) {
				safeRemoveModifier(player, modifier);
			}
			GexpressTestState.unmarkModifier(player.getUuid(), modifier);
		}
		mods.sync();

		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Removed test modifier " + id(modifier) + " from " + playerList(players) + ".")
			.formatted(Formatting.YELLOW), true);
		return players.size();
	}

	private static int runClearModifiers(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		if (denyRoleModifierTestDuringGame(src)) return 0;
		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(src.getWorld());
		if (mods == null) {
			src.sendError(Text.literal("HML modifier component is not available in this world."));
			return 0;
		}

		for (ServerPlayerEntity player : players) {
			ArrayList<Modifier> current = safeModifiers(mods, player);
			List<Modifier> removed = new ArrayList<>(current);
			current.clear();
			for (Modifier modifier : removed) {
				safeRemoveModifier(player, modifier);
			}
			GexpressTestState.clearModifiers(player.getUuid());
		}
		mods.sync();

		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Cleared test modifiers for " + playerList(players) + ".")
			.formatted(Formatting.YELLOW), true);
		return players.size();
	}

	private static int runAttachC4(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		C4BackComponent comp = C4BackComponent.KEY.get(ctx.getSource().getWorld());
		ServerPlayerEntity owner = ctx.getSource().getEntity() instanceof ServerPlayerEntity player ? player : null;
		int changed = 0;
		for (ServerPlayerEntity target : players) {
			if (owner == null ? comp.addC4(target.getUuid()) : comp.addC4(target.getUuid(), owner.getUuid())) changed++;
		}
		int count = changed;
		dev.mapselect.command.GexpressFeedback.send(ctx.getSource(), () -> Text.literal("Attached test C4 to " + count + " player(s).")
			.formatted(Formatting.GREEN), true);
		return changed;
	}

	private static int runRemoveC4(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		C4BackComponent comp = C4BackComponent.KEY.get(ctx.getSource().getWorld());
		int changed = 0;
		for (ServerPlayerEntity target : players) {
			if (comp.removeC4(target.getUuid())) changed++;
		}
		int count = changed;
		dev.mapselect.command.GexpressFeedback.send(ctx.getSource(), () -> Text.literal("Removed test C4 from " + count + " player(s).")
			.formatted(count > 0 ? Formatting.YELLOW : Formatting.GRAY), true);
		return changed;
	}

	private static int runClearC4(CommandContext<ServerCommandSource> ctx) {
		C4BackComponent comp = C4BackComponent.KEY.get(ctx.getSource().getWorld());
		int count = comp.getCarriers().size();
		if (comp.clearAll()) {
			dev.mapselect.command.GexpressFeedback.send(ctx.getSource(), () -> Text.literal("Cleared test C4 from " + count + " player(s).")
				.formatted(Formatting.YELLOW), true);
		} else {
			dev.mapselect.command.GexpressFeedback.send(ctx.getSource(), () -> Text.literal("No test C4 was attached.").formatted(Formatting.GRAY), false);
		}
		return count;
	}

	private static int runReleaseFromPelican(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		int released = 0;
		for (ServerPlayerEntity player : players) {
			if (PelicanManager.releaseFromBelly(player, true)) released++;
		}
		int count = released;
		dev.mapselect.command.GexpressFeedback.send(ctx.getSource(), () -> Text.literal("Released " + count + " player(s) from Pelicans.")
			.formatted(count > 0 ? Formatting.GREEN : Formatting.GRAY), true);
		return released;
	}

	private static int runReduceCooldowns(CommandContext<ServerCommandSource> ctx, int seconds,
			Collection<ServerPlayerEntity> players) {
		long ticks = (long) seconds * 20L;
		for (ServerPlayerEntity player : players) {
			AbilityCooldownReducers.reduce(player, ticks);
		}
		dev.mapselect.command.GexpressFeedback.send(ctx.getSource(), () -> Text.literal((seconds >= Integer.MAX_VALUE / 20
				? "Cleared ability cooldowns for "
				: "Reduced ability cooldowns by " + seconds + "s for ")
				+ playerList(players) + ".").formatted(Formatting.GREEN), true);
		return players.size();
	}

	private static int runRewindOverlay(CommandContext<ServerCommandSource> ctx, int seconds,
			Collection<ServerPlayerEntity> players) {
		int sent = 0;
		TimeMasterRewindPayload payload = new TimeMasterRewindPayload(toTicks(seconds));
		for (ServerPlayerEntity player : players) {
			if (sendPayload(player, payload)) sent++;
		}
		sendOverlayFeedback(ctx.getSource(), "rewind", seconds, sent);
		return sent;
	}

	private static int runFreezeOverlay(CommandContext<ServerCommandSource> ctx, int seconds,
			Collection<ServerPlayerEntity> players) {
		ServerPlayerEntity sourcePlayer = ctx.getSource().getEntity() instanceof ServerPlayerEntity player ? player : null;
		UUID timeMasterId = sourcePlayer == null ? null : sourcePlayer.getUuid();
		int sent = 0;
		for (ServerPlayerEntity player : players) {
			TimeMasterFreezeStatePayload payload = new TimeMasterFreezeStatePayload(true,
				player.getUuid(), timeMasterId, toTicks(seconds));
			if (sendPayload(player, payload)) sent++;
		}
		sendOverlayFeedback(ctx.getSource(), "freeze", seconds, sent);
		return sent;
	}

	private static int runUnfreezeOverlay(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		int sent = 0;
		for (ServerPlayerEntity player : players) {
			if (sendPayload(player, TimeMasterFreezeStatePayload.clear(player.getUuid()))) sent++;
		}
		int count = sent;
		dev.mapselect.command.GexpressFeedback.send(ctx.getSource(), () -> Text.literal("Cleared freeze overlay for " + count + " player(s).")
			.formatted(count > 0 ? Formatting.YELLOW : Formatting.GRAY), true);
		return sent;
	}

	private static int runMafiaIntroOverlay(CommandContext<ServerCommandSource> ctx, int seconds,
			Collection<ServerPlayerEntity> players) {
		int sent = 0;
		MafiaIntroPayload payload = new MafiaIntroPayload(toTicks(seconds));
		for (ServerPlayerEntity player : players) {
			if (sendPayload(player, payload)) sent++;
		}
		sendOverlayFeedback(ctx.getSource(), "mafia intro", seconds, sent);
		return sent;
	}

	private static int runBlackWhiteOverlay(CommandContext<ServerCommandSource> ctx, int seconds,
			Collection<ServerPlayerEntity> players) {
		return runTestOverlay(ctx, "black_white", "black-white shader", seconds, players);
	}

	private static int runCupidOverlay(CommandContext<ServerCommandSource> ctx, int seconds,
			Collection<ServerPlayerEntity> players) {
		return runTestOverlay(ctx, "cupid", "cupid shader", seconds, players);
	}

	private static int runClearOverlay(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		int sent = 0;
		TestOverlayPayload payload = new TestOverlayPayload("clear", 0);
		for (ServerPlayerEntity player : players) {
			if (sendPayload(player, payload)) sent++;
		}
		for (ServerPlayerEntity player : players) {
			sendPayload(player, TimeMasterFreezeStatePayload.clear(player.getUuid()));
		}
		int count = sent;
		dev.mapselect.command.GexpressFeedback.send(ctx.getSource(), () -> Text.literal("Cleared test overlays for " + count + " player(s).")
			.formatted(count > 0 ? Formatting.YELLOW : Formatting.GRAY), true);
		return sent;
	}

	private static int runTestOverlay(CommandContext<ServerCommandSource> ctx, String overlay,
			String label, int seconds, Collection<ServerPlayerEntity> players) {
		int sent = 0;
		TestOverlayPayload payload = new TestOverlayPayload(overlay, toTicks(seconds));
		for (ServerPlayerEntity player : players) {
			if (sendPayload(player, payload)) sent++;
		}
		sendOverlayFeedback(ctx.getSource(), label, seconds, sent);
		return sent;
	}

	private static int runClearAll(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		if (denyRoleModifierTestDuringGame(ctx.getSource())) return 0;
		runClearRole(ctx, players);
		runClearModifiers(ctx, players);
		runClearTasks(ctx, players);
		runRemoveC4(ctx, players);
		runReleaseFromPelican(ctx, players);
		runReduceCooldowns(ctx, Integer.MAX_VALUE / 20, players);
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

		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Forced task " + taskName(forcedTask) + " for " + playerList(players) + ".")
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

		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Cleared tasks for " + playerList(players) + ".")
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
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Applied test " + type + " " + value + " to " + playerList(players) + ".")
			.formatted(Formatting.GREEN), true);
	}

	private static void sendOverlayFeedback(ServerCommandSource src, String overlay, int seconds, int sent) {
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Sent " + overlay + " overlay for " + seconds + "s to "
				+ sent + " player(s).")
			.formatted(sent > 0 ? Formatting.GREEN : Formatting.GRAY), true);
	}

	private static int toTicks(int seconds) {
		return Math.max(1, seconds) * 20;
	}

	@SuppressWarnings("rawtypes")
	private static boolean sendPayload(ServerPlayerEntity player, CustomPayload payload) {
		CustomPayload.Id id = payload.getId();
		if (!ServerPlayNetworking.canSend(player, id)) return false;
		ServerPlayNetworking.send(player, payload);
		return true;
	}

	private static boolean denyRoleModifierTestDuringGame(ServerCommandSource src) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(src.getWorld());
		if (game == null) return false;
		if (GexpressGameModes.isTest(game)) return false;
		GameWorldComponent.GameStatus status = game.getGameStatus();
		boolean running = status == GameWorldComponent.GameStatus.STARTING
			|| status == GameWorldComponent.GameStatus.ACTIVE
			|| status == GameWorldComponent.GameStatus.STOPPING;
		if (!running) return false;
		src.sendError(Text.literal("Role and modifier test commands are disabled while a game is running."));
		return true;
	}

	private static void clearWarlockState(ServerCommandSource src, ServerPlayerEntity player) {
		WarlockComponent comp = WarlockComponent.KEY.getNullable(src.getWorld());
		if (comp != null) {
			comp.removeWarlock(player.getUuid());
		}
	}

	private static void addModifier(WorldModifierComponent modifiers, ServerPlayerEntity player, Modifier modifier) {
		if (isStupidExpressPairModifier(modifier)) {
			addStupidExpressLovers(modifiers, player, modifier);
			return;
		}
		modifiers.addModifier(player.getUuid(), modifier);
		safeAssignModifier(player, modifier);
	}

	private static void addStupidExpressLovers(WorldModifierComponent modifiers, ServerPlayerEntity player,
			Modifier modifier) {
		ServerPlayerEntity partner = findStupidExpressLoverPartner(modifiers, player, modifier);
		if (partner == null) {
			player.sendMessage(Text.literal("No eligible innocent lover partner was found.").formatted(Formatting.RED), false);
			return;
		}
		modifiers.addModifier(player.getUuid(), modifier);
		modifiers.addModifier(partner.getUuid(), modifier);
		safeAssignModifier(player, modifier);
		safeAssignModifier(partner, modifier);
	}

	private static ServerPlayerEntity findStupidExpressLoverPartner(WorldModifierComponent modifiers,
			ServerPlayerEntity player, Modifier modifier) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return null;
		List<ServerPlayerEntity> candidates = new ArrayList<>();
		for (ServerPlayerEntity candidate : player.getServerWorld().getPlayers()) {
			if (candidate == player || modifiers.isModifier(candidate.getUuid(), modifier)) continue;
			if (game.getRole(candidate) == null || !game.isInnocent(candidate)) continue;
			if (game.isRole(candidate, WatheRoles.VIGILANTE)) continue;
			candidates.add(candidate);
		}
		if (candidates.isEmpty()) return null;
		return candidates.get((int) (Math.random() * candidates.size()));
	}

	private static void safeAssignModifier(ServerPlayerEntity player, Modifier modifier) {
		try {
			ModifierAssigned.EVENT.invoker().assignModifier(player, modifier);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierAssigned listener failed during /g test modifier add for {} on {}.",
				id(modifier), player.getName().getString(), t);
		}
	}

	private static ArrayList<Modifier> safeModifiers(WorldModifierComponent component, ServerPlayerEntity player) {
		if (component == null || player == null) return new ArrayList<>();
		ArrayList<Modifier> current = component.getModifiers(player.getUuid());
		return current == null ? new ArrayList<>() : current;
	}

	private static void safeRemoveModifier(ServerPlayerEntity player, Modifier modifier) {
		try {
			ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierRemoved listener failed during /g test modifier remove for {} on {}.",
				id(modifier), player.getName().getString(), t);
		}
		player.calculateDimensions();
		player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
	}

	private static boolean isStupidExpressPairModifier(Modifier modifier) {
		if (modifier == null || modifier.identifier() == null) return false;
		Identifier id = modifier.identifier();
		String path = id.getPath().toLowerCase(Locale.ROOT);
		return id.getNamespace().toLowerCase(Locale.ROOT).contains("stupid")
			&& (path.contains("lover") || path.contains("initiate"));
	}

	private static String id(Role role) {
		return role == null ? "(none)" : role.identifier().toString();
	}

	private static String id(Modifier modifier) {
		return modifier == null ? "(none)" : modifier.identifier().toString();
	}

	private static String normalizePresetName(String raw) {
		return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
	}

	private static Role roleById(Identifier id) {
		if (id == null) return null;
		for (Role role : WatheRoles.ROLES) {
			if (role != null && id.equals(role.identifier())) return role;
		}
		return null;
	}

	private static Modifier modifierById(Identifier id) {
		if (id == null) return null;
		for (Modifier modifier : HMLModifiers.MODIFIERS) {
			if (modifier != null && id.equals(modifier.identifier())) return modifier;
		}
		return null;
	}

	private static String playerList(Collection<ServerPlayerEntity> players) {
		List<String> names = new ArrayList<>();
		for (ServerPlayerEntity player : players) {
			names.add(player.getName().getString());
		}
		return String.join(", ", names);
	}

	private record TestPreset(Identifier roleId, List<Identifier> modifierIds) {}
}
