package dev.mapselect.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.mapselect.config.RoleModifierTuningConfig;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.role.RoleModifierTuningBridge;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.modifiers.HMLModifiers;

public class TuningCommand {
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

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("tuning")
			.requires(GexpressPermissions::canUseHostCommands)
			.then(roleBranch())
			.then(modifierBranch());
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildRoleTree() {
		return CommandManager.literal("tuning")
			.requires(GexpressPermissions::canUseHostCommands)
			.then(roleBranch());
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildModifierTree() {
		return CommandManager.literal("tuning")
			.requires(GexpressPermissions::canUseHostCommands)
			.then(modifierBranch());
	}

	private static LiteralArgumentBuilder<ServerCommandSource> roleBranch() {
		return CommandManager.literal("role")
				.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
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
								IntegerArgumentType.getInteger(ctx, "value")))))
					.then(CommandManager.literal("amount")
						.then(CommandManager.argument("value", IntegerArgumentType.integer(
								RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX))
							.executes(ctx -> setRoleMax(ctx,
								IdentifierArgumentType.getIdentifier(ctx, "id").toString(),
								IntegerArgumentType.getInteger(ctx, "value"))))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> modifierBranch() {
		return CommandManager.literal("modifier")
				.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
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
								IntegerArgumentType.getInteger(ctx, "value")))))
					.then(CommandManager.literal("amount")
						.then(CommandManager.argument("value", IntegerArgumentType.integer(
								RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX))
							.executes(ctx -> setModifierMax(ctx,
								IdentifierArgumentType.getIdentifier(ctx, "id").toString(),
								IntegerArgumentType.getInteger(ctx, "value"))))));
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
		ctx.getSource().sendFeedback(() -> Text.literal(label + " for " + id + " set to " + value + ".")
			.formatted(Formatting.GREEN), true);
	}
}
