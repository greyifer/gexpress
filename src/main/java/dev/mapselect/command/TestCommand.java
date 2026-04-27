package dev.mapselect.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.MapSelect;
import dev.mapselect.permissions.GexpressPermissions;
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
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class TestCommand {
	private static final Predicate<ServerCommandSource> OP = GexpressPermissions::canUseAdminCommands;

	private TestCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
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
							EntityArgumentType.getPlayers(ctx, "players"))))))
			.then(CommandManager.literal("modifier")
				.then(CommandManager.literal("add")
					.then(CommandManager.argument("modifier", ModifierArgumentType.create())
						.executes(ctx -> runAddModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"), self(ctx)))
						.then(CommandManager.argument("players", EntityArgumentType.players())
							.executes(ctx -> runAddModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"),
								EntityArgumentType.getPlayers(ctx, "players"))))))
				.then(CommandManager.literal("remove")
					.then(CommandManager.argument("modifier", ModifierArgumentType.create())
						.executes(ctx -> runRemoveModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"), self(ctx)))
						.then(CommandManager.argument("players", EntityArgumentType.players())
							.executes(ctx -> runRemoveModifier(ctx, ModifierArgumentType.getModifier(ctx, "modifier"),
								EntityArgumentType.getPlayers(ctx, "players"))))))
				.then(CommandManager.literal("clear")
					.executes(ctx -> runClearModifiers(ctx, self(ctx)))
					.then(CommandManager.argument("players", EntityArgumentType.players())
						.executes(ctx -> runClearModifiers(ctx, EntityArgumentType.getPlayers(ctx, "players"))))));
	}

	private static Collection<ServerPlayerEntity> self(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		return List.of(ctx.getSource().getPlayerOrThrow());
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
			MapSelect.LOGGER.warn("ModifierAssigned listener failed during /g test modifier add for {} on {}.",
				id(modifier), player.getName().getString(), t);
		}
	}

	private static void safeRemoveModifier(ServerPlayerEntity player, Modifier modifier) {
		try {
			ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierRemoved listener failed during /g test modifier remove for {} on {}.",
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
