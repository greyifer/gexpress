package dev.mapselect.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.mapselect.item.DevWeaponSkinStamper;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.skin.PlayerSkinComponent;
import dev.mapselect.skin.WeaponSkin;
import dev.mapselect.skin.WeaponSkinType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SkinCommand {
	private static final List<String> TYPES = Arrays.stream(WeaponSkinType.values()).map(WeaponSkinType::id).toList();
	private static final List<String> SKINS = Arrays.stream(WeaponSkin.values()).map(WeaponSkin::id).toList();

	private SkinCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("skins")
			.then(CommandManager.literal("equip")
				.then(CommandManager.argument("type", StringArgumentType.word())
					.suggests(SkinCommand::suggestTypes)
					.then(CommandManager.argument("skin", StringArgumentType.word())
						.suggests(SkinCommand::suggestSkins)
						.executes(SkinCommand::runEquip))))
			.then(CommandManager.literal("list")
				.executes(SkinCommand::runList))
			.then(CommandManager.literal("give")
				.requires(GexpressPermissions::canUseAdminCommands)
				.then(CommandManager.argument("type", StringArgumentType.word())
					.suggests(SkinCommand::suggestTypes)
					.then(CommandManager.argument("skin", StringArgumentType.word())
						.suggests(SkinCommand::suggestGrantableSkins)
						.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
							.executes(ctx -> runGrant(ctx, true))))))
			.then(CommandManager.literal("remove")
				.requires(GexpressPermissions::canUseAdminCommands)
				.then(CommandManager.argument("type", StringArgumentType.word())
					.suggests(SkinCommand::suggestTypes)
					.then(CommandManager.argument("skin", StringArgumentType.word())
						.suggests(SkinCommand::suggestGrantableSkins)
						.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
							.executes(ctx -> runGrant(ctx, false))))));
	}

	private static CompletableFuture<Suggestions> suggestTypes(CommandContext<ServerCommandSource> ctx,
			SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(TYPES, builder);
	}

	private static CompletableFuture<Suggestions> suggestSkins(CommandContext<ServerCommandSource> ctx,
			SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(skinsForType(ctx, true), builder);
	}

	private static CompletableFuture<Suggestions> suggestGrantableSkins(CommandContext<ServerCommandSource> ctx,
			SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(skinsForType(ctx, false), builder);
	}

	private static int runEquip(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		WeaponSkinType type = type(ctx);
		WeaponSkin skin = skin(ctx);
		if (type == null || skin == null) {
			ctx.getSource().sendError(Text.literal("Use type knife/gun and skin " + String.join(", ", SKINS) + "."));
			return 0;
		}
		if (!skin.supports(type)) {
			ctx.getSource().sendError(Text.literal(skin.displayName() + " is not a " + type.displayName() + " skin."));
			return 0;
		}
		PlayerSkinComponent component = PlayerSkinComponent.KEY.get(player.getServerWorld());
		if (!component.isUnlocked(player.getUuid(), type, skin)) {
			ctx.getSource().sendError(Text.literal("You have not unlocked " + skin.displayName() + " for " + type.displayName() + "."));
			return 0;
		}
		component.equip(player.getUuid(), type, skin);
		DevWeaponSkinStamper.stamp(player);
		player.sendMessage(Text.literal(type.displayName() + " skin equipped: " + skin.displayName() + ".")
			.formatted(Formatting.GREEN), false);
		return 1;
	}

	private static int runGrant(CommandContext<ServerCommandSource> ctx, boolean give)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		WeaponSkinType type = type(ctx);
		WeaponSkin skin = skin(ctx);
		if (type == null || skin == null || skin == WeaponSkin.DEFAULT || !skin.supports(type)) {
			ctx.getSource().sendError(Text.literal("Use type knife/gun and a non-default skin."));
			return 0;
		}
		PlayerSkinComponent component = PlayerSkinComponent.KEY.get(ctx.getSource().getWorld());
		Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int changed = 0;
		for (GameProfile profile : profiles) {
			boolean ok = give
				? component.give(profile.getId(), type, skin)
				: component.remove(profile.getId(), type, skin);
			if (ok) changed++;
			ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(profile.getId());
			if (online != null) DevWeaponSkinStamper.stamp(online);
		}
		int finalChanged = changed;
		ctx.getSource().sendFeedback(() -> Text.literal((give ? "Gave " : "Removed ")
			+ skin.displayName() + " " + type.displayName() + " skin for " + finalChanged + " player(s).")
			.formatted(Formatting.GREEN), true);
		return changed;
	}

	private static int runList(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		PlayerSkinComponent component = PlayerSkinComponent.KEY.get(player.getServerWorld());
		String knife = component.unlocked(player.getUuid(), WeaponSkinType.KNIFE).stream().map(WeaponSkin::displayName).toList().toString();
		String gun = component.unlocked(player.getUuid(), WeaponSkinType.GUN).stream().map(WeaponSkin::displayName).toList().toString();
		player.sendMessage(Text.literal("Knife skins: " + knife + " | Gun skins: " + gun).formatted(Formatting.GRAY), false);
		return 1;
	}

	private static WeaponSkinType type(CommandContext<ServerCommandSource> ctx) {
		return WeaponSkinType.byId(StringArgumentType.getString(ctx, "type"));
	}

	private static WeaponSkin skin(CommandContext<ServerCommandSource> ctx) {
		return WeaponSkin.byId(StringArgumentType.getString(ctx, "skin"));
	}

	private static List<String> skinsForType(CommandContext<ServerCommandSource> ctx, boolean includeDefault) {
		WeaponSkinType type = type(ctx);
		if (type == null) return includeDefault ? SKINS : SKINS.stream().filter(id -> !"default".equals(id)).toList();
		return Arrays.stream(WeaponSkin.values())
			.filter(skin -> includeDefault || skin != WeaponSkin.DEFAULT)
			.map(skin -> skin.logical(type))
			.distinct()
			.filter(skin -> skin.visibleInPicker(type))
			.map(WeaponSkin::id)
			.toList();
	}
}
