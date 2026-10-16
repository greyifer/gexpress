package dev.mapselect.command.admin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.mapselect.config.RoleModifierTuningConfig;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.role.RoleModifierTuningBridge;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.modifiers.HMLModifiers;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

public final class TuningCommand {
	private static final SuggestionProvider<ServerCommandSource> ROLE_SUGGESTIONS = (ctx, builder) -> {
		for (var role : WatheRoles.ROLES) {
			builder.suggest(role.identifier().toString());
		}
		return builder.buildFuture();
	};

	private static final SuggestionProvider<ServerCommandSource> MODIFIER_SUGGESTIONS = (ctx, builder) -> {
		for (var modifier : HMLModifiers.MODIFIERS) {
			builder.suggest(modifier.identifier.toString());
		}
		return builder.buildFuture();
	};

	private static final SuggestionProvider<ServerCommandSource> MAP_SUGGESTIONS = (ctx, builder) -> {
		try {
			for (String name : PresetStorage.list(ctx.getSource().getServer())) {
				builder.suggest(name);
			}
		} catch (IOException ignored) {
		}
		return builder.buildFuture();
	};

	private TuningCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("tuning")
			.requires(GexpressPermissions::canUseAdminCommands)
			.then(roleBranch())
			.then(modifierBranch())
			.then(mapBranch());
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildRoleTree() {
		return CommandManager.literal("tuning")
			.requires(source -> GexpressPermissions.canUseRoleCommands(source)
				|| GexpressPermissions.canUseCommandBranch(source, "roles", "tuning"))
			.then(roleTarget());
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildModifierTree() {
		return CommandManager.literal("tuning")
			.requires(source -> GexpressPermissions.canUseModifierCommands(source)
				|| GexpressPermissions.canUseCommandBranch(source, "modifiers", "tuning"))
			.then(modifierTarget());
	}

	private static LiteralArgumentBuilder<ServerCommandSource> roleBranch() {
		return CommandManager.literal("role")
			.then(roleTarget());
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Identifier> roleTarget() {
		return CommandManager.argument("id", IdentifierArgumentType.identifier())
			.suggests(ROLE_SUGGESTIONS)
			.then(CommandManager.literal("chance")
				.then(CommandManager.argument("value", IntegerArgumentType.integer(
						RoleModifierTuningConfig.CHANCE_MIN, RoleModifierTuningConfig.CHANCE_MAX))
					.executes(ctx -> setRoleChance(ctx,
						IdentifierArgumentType.getIdentifier(ctx, "id").toString(),
						IntegerArgumentType.getInteger(ctx, "value")))))
			.then(CommandManager.literal("max")
				.then(CommandManager.argument("value", IntegerArgumentType.integer(
						RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX))
					.executes(ctx -> setRoleMax(ctx,
						IdentifierArgumentType.getIdentifier(ctx, "id").toString(),
						IntegerArgumentType.getInteger(ctx, "value")))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> modifierBranch() {
		return CommandManager.literal("modifier")
			.then(modifierTarget());
	}

	private static LiteralArgumentBuilder<ServerCommandSource> mapBranch() {
		return CommandManager.literal("map")
			.then(CommandManager.argument("preset", StringArgumentType.word())
				.suggests(MAP_SUGGESTIONS)
				.then(CommandManager.literal("role")
					.then(mapRoleTarget()))
				.then(CommandManager.literal("modifier")
					.then(mapModifierTarget())));
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Identifier> modifierTarget() {
		return CommandManager.argument("id", IdentifierArgumentType.identifier())
			.suggests(MODIFIER_SUGGESTIONS)
			.then(CommandManager.literal("chance")
				.then(CommandManager.argument("value", IntegerArgumentType.integer(
						RoleModifierTuningConfig.CHANCE_MIN, RoleModifierTuningConfig.CHANCE_MAX))
					.executes(ctx -> setModifierChance(ctx,
						IdentifierArgumentType.getIdentifier(ctx, "id").toString(),
						IntegerArgumentType.getInteger(ctx, "value")))))
			.then(CommandManager.literal("max")
				.then(CommandManager.argument("value", IntegerArgumentType.integer(
						RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX))
					.executes(ctx -> setModifierMax(ctx,
						IdentifierArgumentType.getIdentifier(ctx, "id").toString(),
						IntegerArgumentType.getInteger(ctx, "value")))));
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Identifier> mapRoleTarget() {
		return CommandManager.argument("id", IdentifierArgumentType.identifier())
			.suggests(ROLE_SUGGESTIONS)
			.then(CommandManager.literal("chance")
				.then(CommandManager.argument("value", IntegerArgumentType.integer(
						RoleModifierTuningConfig.CHANCE_MIN, RoleModifierTuningConfig.CHANCE_MAX))
					.executes(ctx -> setMapOverride(ctx, "Map role chance", preset -> preset.roleChanceOverrides,
						IdentifierArgumentType.getIdentifier(ctx, "id").toString(),
						IntegerArgumentType.getInteger(ctx, "value"), "%"))))
			.then(CommandManager.literal("max")
				.then(CommandManager.argument("value", IntegerArgumentType.integer(
						RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX))
					.executes(ctx -> setMapOverride(ctx, "Map role amount", preset -> preset.roleMaxOverrides,
						IdentifierArgumentType.getIdentifier(ctx, "id").toString(),
						IntegerArgumentType.getInteger(ctx, "value"), ""))))
			;
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Identifier> mapModifierTarget() {
		return CommandManager.argument("id", IdentifierArgumentType.identifier())
			.suggests(MODIFIER_SUGGESTIONS)
			.then(CommandManager.literal("chance")
				.then(CommandManager.argument("value", IntegerArgumentType.integer(
						RoleModifierTuningConfig.CHANCE_MIN, RoleModifierTuningConfig.CHANCE_MAX))
					.executes(ctx -> setMapOverride(ctx, "Map modifier chance", preset -> preset.modifierChanceOverrides,
						IdentifierArgumentType.getIdentifier(ctx, "id").toString(),
						IntegerArgumentType.getInteger(ctx, "value"), "%"))))
			.then(CommandManager.literal("max")
				.then(CommandManager.argument("value", IntegerArgumentType.integer(
						RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX))
					.executes(ctx -> setMapOverride(ctx, "Map modifier amount", preset -> preset.modifierMaxOverrides,
						IdentifierArgumentType.getIdentifier(ctx, "id").toString(),
						IntegerArgumentType.getInteger(ctx, "value"), ""))))
			;
	}

	private static int setRoleChance(CommandContext<ServerCommandSource> ctx, String id, int value) {
		if (!validId(ctx, id)) return 0;
		RoleModifierTuningConfig.setRoleChance(id, value);
		saveAndApply();
		feedback(ctx, "Role chance", id, value + "%");
		return 1;
	}

	private static int setRoleMax(CommandContext<ServerCommandSource> ctx, String id, int value) {
		if (!validId(ctx, id)) return 0;
		RoleModifierTuningConfig.setRoleMax(id, value);
		saveAndApply();
		feedback(ctx, "Role amount", id, Integer.toString(value));
		return 1;
	}

	private static int setModifierChance(CommandContext<ServerCommandSource> ctx, String id, int value) {
		if (!validId(ctx, id)) return 0;
		RoleModifierTuningConfig.setModifierChance(id, value);
		saveAndApply();
		feedback(ctx, "Modifier chance", id, value + "%");
		return 1;
	}

	private static int setModifierMax(CommandContext<ServerCommandSource> ctx, String id, int value) {
		if (!validId(ctx, id)) return 0;
		RoleModifierTuningConfig.setModifierMax(id, value);
		saveAndApply();
		feedback(ctx, "Modifier amount", id, Integer.toString(value));
		return 1;
	}

	private static int setMapOverride(CommandContext<ServerCommandSource> ctx, String label,
			Function<MapPreset, Map<String, Integer>> target, String id, int value, String suffix) {
		if (!validId(ctx, id)) return 0;
		String presetName = StringArgumentType.getString(ctx, "preset");
		try {
			MapPreset preset = PresetStorage.load(ctx.getSource().getServer(), presetName);
			if (preset == null) {
				ctx.getSource().sendError(Text.literal("Map preset not found: " + presetName));
				return 0;
			}
			preset.normalize();
			target.apply(preset).put(id, value);
			PresetStorage.save(ctx.getSource().getServer(), presetName, preset);
			RoleModifierTuningBridge.prepareForGame(ctx.getSource().getWorld(), -1);
			feedback(ctx, label, presetName + " / " + id, value + suffix);
			return 1;
		} catch (IOException e) {
			ctx.getSource().sendError(Text.literal("Failed to update map preset: " + e.getMessage()));
			return 0;
		}
	}

	private static boolean validId(CommandContext<ServerCommandSource> ctx, String id) {
		if (Identifier.tryParse(id) != null) return true;
		ctx.getSource().sendError(Text.literal("Invalid identifier: " + id));
		return false;
	}

	private static void saveAndApply() {
		RoleModifierTuningConfig.save();
		RoleModifierTuningBridge.applyConfiguredMaxima();
	}

	private static void feedback(CommandContext<ServerCommandSource> ctx, String label, String id, String value) {
		dev.mapselect.command.GexpressFeedback.send(ctx.getSource(), () -> Text.literal(label + " for " + id + " set to " + value + ".")
			.formatted(Formatting.GREEN), true);
	}
}
