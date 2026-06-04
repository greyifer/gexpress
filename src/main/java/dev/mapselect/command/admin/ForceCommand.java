package dev.mapselect.command.admin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.MapSelect;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ForceCommand {
	private static final DynamicCommandExceptionType UNKNOWN_ROLE = new DynamicCommandExceptionType(
		value -> Text.literal("Unknown role: " + value));
	private static final DynamicCommandExceptionType AMBIGUOUS_ROLE = new DynamicCommandExceptionType(
		value -> Text.literal("Role is ambiguous, use the full id: " + value));
	private static final DynamicCommandExceptionType UNKNOWN_MODIFIER = new DynamicCommandExceptionType(
		value -> Text.literal("Unknown modifier: " + value));
	private static final DynamicCommandExceptionType AMBIGUOUS_MODIFIER = new DynamicCommandExceptionType(
		value -> Text.literal("Modifier is ambiguous, use the full id: " + value));

	private ForceCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("force")
			.requires(GexpressPermissions::canUseAdminCommands)
			.then(roleBranch())
			.then(modifierBranch());
	}

	private static LiteralArgumentBuilder<ServerCommandSource> roleBranch() {
		return CommandManager.literal("role")
			.then(CommandManager.literal("clear")
				.executes(ctx -> runClearRole(ctx, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runClearRole(ctx, EntityArgumentType.getPlayers(ctx, "players")))))
			.then(CommandManager.argument("role", StringArgumentType.string())
				.suggests(suggestRoles())
				.executes(ctx -> runSetRole(ctx, getRole(ctx, "role"), self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runSetRole(ctx, getRole(ctx, "role"),
						EntityArgumentType.getPlayers(ctx, "players")))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> modifierBranch() {
		return CommandManager.literal("modifier")
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.then(CommandManager.argument("modifier", StringArgumentType.string())
						.suggests(suggestModifiers())
						.executes(ctx -> runAddModifier(ctx, getModifier(ctx, "modifier"),
							EntityArgumentType.getPlayers(ctx, "players")))))
				.then(CommandManager.argument("modifier", StringArgumentType.string())
					.suggests(suggestModifiers())
					.executes(ctx -> runAddModifier(ctx, getModifier(ctx, "modifier"), self(ctx)))
					.then(CommandManager.argument("players", EntityArgumentType.players())
						.executes(ctx -> runAddModifier(ctx, getModifier(ctx, "modifier"),
							EntityArgumentType.getPlayers(ctx, "players"))))))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.then(CommandManager.argument("modifier", StringArgumentType.string())
						.suggests(suggestModifiers())
						.executes(ctx -> runRemoveModifier(ctx, getModifier(ctx, "modifier"),
							EntityArgumentType.getPlayers(ctx, "players")))))
				.then(CommandManager.argument("modifier", StringArgumentType.string())
					.suggests(suggestModifiers())
					.executes(ctx -> runRemoveModifier(ctx, getModifier(ctx, "modifier"), self(ctx)))
					.then(CommandManager.argument("players", EntityArgumentType.players())
						.executes(ctx -> runRemoveModifier(ctx, getModifier(ctx, "modifier"),
							EntityArgumentType.getPlayers(ctx, "players"))))))
			.then(CommandManager.literal("clear")
				.executes(ctx -> runClearModifiers(ctx, self(ctx)))
				.then(CommandManager.argument("players", EntityArgumentType.players())
					.executes(ctx -> runClearModifiers(ctx, EntityArgumentType.getPlayers(ctx, "players")))));
	}

	private static Collection<ServerPlayerEntity> self(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		return List.of(ctx.getSource().getPlayerOrThrow());
	}

	private static Role getRole(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
		return resolveRole(StringArgumentType.getString(ctx, name));
	}

	private static Modifier getModifier(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
		return resolveModifier(StringArgumentType.getString(ctx, name));
	}

	private static SuggestionProvider<ServerCommandSource> suggestRoles() {
		return (ctx, builder) -> {
			Harpymodloader.refreshRoles();
			String remaining = builder.getRemainingLowerCase();
			WatheRoles.ROLES.stream()
				.filter(role -> role != null && role.identifier() != null)
				.sorted(Comparator.comparing(role -> role.identifier().toString()))
				.map(role -> role.identifier().toString())
				.filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
				.forEach(builder::suggest);
			return builder.buildFuture();
		};
	}

	private static SuggestionProvider<ServerCommandSource> suggestModifiers() {
		return (ctx, builder) -> {
			String remaining = builder.getRemainingLowerCase();
			HMLModifiers.MODIFIERS.stream()
				.sorted(Comparator.comparing(modifier -> modifier.identifier().toString()))
				.map(modifier -> modifier.identifier().toString())
				.filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
				.forEach(builder::suggest);
			return builder.buildFuture();
		};
	}

	private static Role resolveRole(String raw) throws CommandSyntaxException {
		Harpymodloader.refreshRoles();
		List<Role> matches = new ArrayList<>();
		Identifier id = Identifier.tryParse(raw);
		for (Role role : WatheRoles.ROLES) {
			if (role == null || role.identifier() == null) continue;
			if (role.identifier().equals(id) || role.identifier().getPath().equals(raw)) matches.add(role);
		}
		if (matches.isEmpty()) {
			String lower = raw.toLowerCase(Locale.ROOT);
			for (Role role : WatheRoles.ROLES) {
				if (role == null || role.identifier() == null) continue;
				String full = role.identifier().toString().toLowerCase(Locale.ROOT);
				String path = role.identifier().getPath().toLowerCase(Locale.ROOT);
				if (full.startsWith(lower) || path.startsWith(lower)) matches.add(role);
			}
		}
		if (matches.isEmpty()) throw UNKNOWN_ROLE.create(raw);
		if (matches.size() > 1) throw AMBIGUOUS_ROLE.create(raw);
		return matches.getFirst();
	}

	private static Modifier resolveModifier(String raw) throws CommandSyntaxException {
		List<Modifier> matches = new ArrayList<>();
		Identifier id = Identifier.tryParse(raw);
		for (Modifier modifier : HMLModifiers.MODIFIERS) {
			if (modifier == null || modifier.identifier() == null) continue;
			if (modifier.identifier().equals(id) || modifier.identifier().getPath().equals(raw)) matches.add(modifier);
		}
		if (matches.isEmpty()) {
			String lower = raw.toLowerCase(Locale.ROOT);
			for (Modifier modifier : HMLModifiers.MODIFIERS) {
				if (modifier == null || modifier.identifier() == null) continue;
				String full = modifier.identifier().toString().toLowerCase(Locale.ROOT);
				String path = modifier.identifier().getPath().toLowerCase(Locale.ROOT);
				if (full.startsWith(lower) || path.startsWith(lower)) matches.add(modifier);
			}
		}
		if (matches.isEmpty()) throw UNKNOWN_MODIFIER.create(raw);
		if (matches.size() > 1) throw AMBIGUOUS_MODIFIER.create(raw);
		return matches.getFirst();
	}

	private static int runSetRole(CommandContext<ServerCommandSource> ctx, Role role,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(src.getWorld());
		if (game == null) {
			src.sendError(Text.literal("WATHE game component is not available in this world."));
			return 0;
		}

		boolean running = game.isRunning();
		for (ServerPlayerEntity player : players) {
			rememberForcedRole(player, role);
			if (running) {
				assignRole(game, player, role);
				player.sendMessage(Text.literal("Forced role: " + id(role)).formatted(Formatting.GREEN), false);
			} else {
				game.addRole(player, role);
			}
		}
		game.sync();

		src.sendFeedback(() -> Text.literal((running ? "Forced live role " : "Queued forced role ")
				+ id(role) + " on " + playerList(players) + ".")
			.formatted(Formatting.GREEN), true);
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
			Role previous = game.getRole(player);
			if (previous != null && !Harpymodloader.VANNILA_ROLES.contains(previous)) {
				safeRemoveRole(player, previous);
			}
			game.getRoles().remove(player.getUuid());
			forgetForcedRole(player);
		}
		game.sync();

		src.sendFeedback(() -> Text.literal("Cleared forced role for " + playerList(players) + ".")
			.formatted(Formatting.YELLOW), true);
		return players.size();
	}

	private static void assignRole(GameWorldComponent game, ServerPlayerEntity player, Role role) {
		if (role == null) return;
		Role previous = game.getRole(player);
		if (previous != null && !Harpymodloader.VANNILA_ROLES.contains(previous)) {
			safeRemoveRole(player, previous);
		}
		game.addRole(player, role);
		if (!Harpymodloader.VANNILA_ROLES.contains(role)) {
			safeAssignRole(player, role);
		}
	}

	private static void rememberForcedRole(ServerPlayerEntity player, Role role) {
		forgetForcedRole(player);
		Harpymodloader.addToForcedRoles(role, player);
	}

	private static void forgetForcedRole(ServerPlayerEntity player) {
		Harpymodloader.FORCED_MODDED_ROLE_FLIP.remove(player.getUuid());
		for (List<java.util.UUID> forcedPlayers : Harpymodloader.FORCED_MODDED_ROLE.values()) {
			forcedPlayers.remove(player.getUuid());
		}
		Harpymodloader.FORCED_MODDED_ROLE.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
	}

	private static int runAddModifier(CommandContext<ServerCommandSource> ctx, Modifier modifier,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(src.getWorld());
		if (mods == null) {
			src.sendError(Text.literal("HML modifier component is not available in this world."));
			return 0;
		}

		int changed = 0;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(src.getWorld());
		boolean running = game != null && game.isRunning();
		for (ServerPlayerEntity player : players) {
			if (!running) {
				rememberForcedModifier(player, modifier);
				changed++;
			} else if (!mods.isModifier(player.getUuid(), modifier)) {
				changed += addModifier(mods, player, modifier);
			}
		}
		mods.sync();

		int count = changed;
		src.sendFeedback(() -> Text.literal((running ? "Forced live modifier " : "Queued forced modifier ")
				+ id(modifier) + " on "
				+ count + " player(s): " + playerList(players) + ".")
			.formatted(count > 0 ? Formatting.GREEN : Formatting.GRAY), true);
		return changed;
	}

	private static int addModifier(WorldModifierComponent modifiers, ServerPlayerEntity player, Modifier modifier) {
		if (isStupidExpressPairModifier(modifier)) {
			return addStupidExpressPairModifier(modifiers, player, modifier);
		}
		modifiers.addModifier(player.getUuid(), modifier);
		safeAssignModifier(player, modifier);
		return 1;
	}

	private static void rememberForcedModifier(ServerPlayerEntity player, Modifier modifier) {
		forgetForcedModifier(player, modifier);
		Harpymodloader.addToForcedModifiers(modifier, player);
	}

	private static void forgetForcedModifier(ServerPlayerEntity player, Modifier modifier) {
		if (player == null || modifier == null) return;
		List<java.util.UUID> forcedPlayers = Harpymodloader.FORCED_MODDED_MODIFIER.get(modifier);
		if (forcedPlayers != null) forcedPlayers.remove(player.getUuid());
		Harpymodloader.FORCED_MODDED_MODIFIER.entrySet()
			.removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
	}

	private static void forgetForcedModifiers(ServerPlayerEntity player) {
		if (player == null) return;
		for (List<java.util.UUID> forcedPlayers : Harpymodloader.FORCED_MODDED_MODIFIER.values()) {
			forcedPlayers.remove(player.getUuid());
		}
		Harpymodloader.FORCED_MODDED_MODIFIER.entrySet()
			.removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
	}

	private static int addStupidExpressPairModifier(WorldModifierComponent modifiers, ServerPlayerEntity player,
			Modifier modifier) {
		ServerPlayerEntity partner = findStupidExpressPairPartner(modifiers, player, modifier);
		if (partner == null) {
			player.sendMessage(Text.literal("No eligible innocent pair partner was found.").formatted(Formatting.RED), false);
			return 0;
		}
		modifiers.addModifier(player.getUuid(), modifier);
		modifiers.addModifier(partner.getUuid(), modifier);
		safeAssignModifier(player, modifier);
		safeAssignModifier(partner, modifier);
		return 2;
	}

	private static ServerPlayerEntity findStupidExpressPairPartner(WorldModifierComponent modifiers,
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

	private static int runRemoveModifier(CommandContext<ServerCommandSource> ctx, Modifier modifier,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(src.getWorld());
		if (mods == null) {
			src.sendError(Text.literal("HML modifier component is not available in this world."));
			return 0;
		}

		int changed = 0;
		for (ServerPlayerEntity player : players) {
			ArrayList<Modifier> current = safeModifiers(mods, player);
			boolean removed = current.removeIf(m -> m == modifier || m.identifier().equals(modifier.identifier()));
			if (removed) {
				safeRemoveModifier(player, modifier);
				changed++;
			}
			forgetForcedModifier(player, modifier);
		}
		mods.sync();

		int count = changed;
		src.sendFeedback(() -> Text.literal("Removed forced modifier " + id(modifier) + " from "
				+ count + " player(s): " + playerList(players) + ".")
			.formatted(count > 0 ? Formatting.YELLOW : Formatting.GRAY), true);
		return changed;
	}

	private static int runClearModifiers(CommandContext<ServerCommandSource> ctx,
			Collection<ServerPlayerEntity> players) {
		ServerCommandSource src = ctx.getSource();
		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(src.getWorld());
		if (mods == null) {
			src.sendError(Text.literal("HML modifier component is not available in this world."));
			return 0;
		}

		int changed = 0;
		for (ServerPlayerEntity player : players) {
			ArrayList<Modifier> current = safeModifiers(mods, player);
			if (current.isEmpty()) {
				forgetForcedModifiers(player);
				continue;
			}
			List<Modifier> removed = new ArrayList<>(current);
			current.clear();
			for (Modifier modifier : removed) {
				safeRemoveModifier(player, modifier);
			}
			forgetForcedModifiers(player);
			changed++;
		}
		mods.sync();

		int count = changed;
		src.sendFeedback(() -> Text.literal("Cleared forced modifiers from "
				+ count + " player(s): " + playerList(players) + ".")
			.formatted(count > 0 ? Formatting.YELLOW : Formatting.GRAY), true);
		return changed;
	}

	private static void safeAssignRole(ServerPlayerEntity player, Role role) {
		try {
			ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModdedRoleAssigned listener failed during /g force role for {} on {}.",
				id(role), player.getName().getString(), t);
		}
	}

	private static void safeRemoveRole(ServerPlayerEntity player, Role role) {
		try {
			ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, role);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModdedRoleRemoved listener failed during /g force role for {} on {}.",
				id(role), player.getName().getString(), t);
		}
	}

	private static void safeAssignModifier(ServerPlayerEntity player, Modifier modifier) {
		try {
			ModifierAssigned.EVENT.invoker().assignModifier(player, modifier);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierAssigned listener failed during /g force modifier add for {} on {}.",
				id(modifier), player.getName().getString(), t);
		}
	}

	private static void safeRemoveModifier(ServerPlayerEntity player, Modifier modifier) {
		try {
			ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierRemoved listener failed during /g force modifier remove for {} on {}.",
				id(modifier), player.getName().getString(), t);
		}
		player.calculateDimensions();
		player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
	}

	private static boolean isStupidExpressPairModifier(Modifier modifier) {
		if (modifier == null || modifier.identifier() == null) return false;
		Identifier id = modifier.identifier();
		String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
		String path = id.getPath().toLowerCase(Locale.ROOT);
		return namespace.contains("stupid") && (path.contains("lover") || path.contains("initiate"));
	}

	private static ArrayList<Modifier> safeModifiers(WorldModifierComponent component, ServerPlayerEntity player) {
		if (component == null || player == null) return new ArrayList<>();
		ArrayList<Modifier> current = component.getModifiers(player.getUuid());
		return current == null ? new ArrayList<>() : current;
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
