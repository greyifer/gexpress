package dev.mapselect.command.admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mapselect.command.GexpressFeedback;
import dev.mapselect.item.KeycardItem;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class KeycardCommand {
	private KeycardCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("keycard")
			.requires(source -> GexpressPermissions.canUseAdminCommands(source)
				|| GexpressPermissions.canUseCommandBranch(source, "admin", "keycard"));

		root.then(CommandManager.literal("give")
			.then(CommandManager.argument("type", StringArgumentType.word())
				.suggests((ctx, builder) -> CommandSource.suggestMatching(tierIds(), builder))
				.then(CommandManager.argument("label", StringArgumentType.greedyString())
					.executes(ctx -> give(ctx,
						requireTier(StringArgumentType.getString(ctx, "type")),
						StringArgumentType.getString(ctx, "label"))))));

		for (KeycardItem.Tier tier : KeycardItem.Tier.values()) {
			root.then(CommandManager.literal(tier.id())
				.then(CommandManager.argument("label", StringArgumentType.greedyString())
					.executes(ctx -> give(ctx, tier, StringArgumentType.getString(ctx, "label")))));
		}
		return root;
	}

	private static int give(CommandContext<ServerCommandSource> ctx, KeycardItem.Tier tier, String label)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		ServerPlayerEntity player = source.getPlayerOrThrow();
		String keyName = KeycardItem.sanitizeKeyName(label);
		if (keyName.isBlank()) {
			GexpressFeedback.send(source, Text.literal("Keycard label cannot be empty.").formatted(Formatting.RED));
			return 0;
		}

		player.getInventory().insertStack(KeycardItem.createStack(tier, keyName));
		GexpressFeedback.send(source, Text.literal("Gave " + tier.displayName() + " for '" + keyName + "'.")
			.formatted(Formatting.GREEN));
		return Command.SINGLE_SUCCESS;
	}

	private static KeycardItem.Tier requireTier(String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		KeycardItem.Tier tier = KeycardItem.Tier.byId(id);
		if (tier != null) return tier;
		throw com.mojang.brigadier.exceptions.CommandSyntaxException.BUILT_IN_EXCEPTIONS
			.dispatcherUnknownArgument().create();
	}

	private static String[] tierIds() {
		KeycardItem.Tier[] tiers = KeycardItem.Tier.values();
		String[] ids = new String[tiers.length];
		for (int i = 0; i < tiers.length; i++) ids[i] = tiers[i].id();
		return ids;
	}
}
