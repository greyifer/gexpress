package dev.mapselect.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.mapselect.host.HostComponent;
import dev.mapselect.host.PlayerTag;
import dev.mapselect.host.PlayerTagComponent;
import dev.mapselect.host.TrustedComponent;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TagCommand {
	private static final List<String> ASSIGNABLE_TAGS = List.of(
		PlayerTag.OWNER.id(),
		PlayerTag.STAFF.id(),
		PlayerTag.HOST.id(),
		PlayerTag.CREATOR.id(),
		PlayerTag.TRUSTED.id(),
		PlayerTag.PASSENGER.id()
	);

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("tag")
			.requires(GexpressPermissions::canEditTags)
			.then(CommandManager.literal("set")
				.then(CommandManager.argument("tag", StringArgumentType.word())
					.suggests(TagCommand::suggestTags)
					.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
						.executes(TagCommand::runSet))))
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("tag", StringArgumentType.word())
					.suggests(TagCommand::suggestTags)
					.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
						.executes(ctx -> runToggle(ctx, true)))))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("tag", StringArgumentType.word())
					.suggests(TagCommand::suggestTags)
					.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
						.executes(ctx -> runToggle(ctx, false)))))
			.then(CommandManager.literal("custom")
				.then(CommandManager.literal("create")
					.then(CommandManager.argument("id", StringArgumentType.word())
						.then(CommandManager.argument("display", StringArgumentType.word())
							.then(CommandManager.argument("color", StringArgumentType.word())
								.then(CommandManager.argument("priority", IntegerArgumentType.integer(1, 99))
									.executes(TagCommand::runCreateCustom))))))
				.then(CommandManager.literal("delete")
					.then(CommandManager.argument("id", StringArgumentType.word())
						.suggests(TagCommand::suggestCustomTags)
						.executes(TagCommand::runDeleteCustom)))
				.then(CommandManager.literal("name")
					.then(CommandManager.argument("id", StringArgumentType.word())
						.suggests(TagCommand::suggestCustomTags)
						.then(CommandManager.argument("display", StringArgumentType.word())
							.executes(TagCommand::runCustomName))))
				.then(CommandManager.literal("color")
					.then(CommandManager.argument("id", StringArgumentType.word())
						.suggests(TagCommand::suggestCustomTags)
						.then(CommandManager.argument("color", StringArgumentType.word())
							.executes(TagCommand::runCustomColor))))
				.then(CommandManager.literal("priority")
					.then(CommandManager.argument("id", StringArgumentType.word())
						.suggests(TagCommand::suggestCustomTags)
						.then(CommandManager.argument("priority", IntegerArgumentType.integer(1, 99))
							.executes(TagCommand::runCustomPriority))))
				.then(CommandManager.literal("permission")
					.then(CommandManager.argument("id", StringArgumentType.word())
						.suggests(TagCommand::suggestCustomTags)
						.then(CommandManager.argument("permission", StringArgumentType.word())
							.suggests(TagCommand::suggestPermissions)
							.then(CommandManager.argument("enabled", BoolArgumentType.bool())
								.executes(TagCommand::runCustomPermission))))))
			.then(CommandManager.literal("list")
				.executes(TagCommand::runList));
	}

	private static CompletableFuture<Suggestions> suggestTags(CommandContext<ServerCommandSource> ctx,
			SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(assignableTags(ctx.getSource()), builder);
	}

	private static CompletableFuture<Suggestions> suggestCustomTags(CommandContext<ServerCommandSource> ctx,
			SuggestionsBuilder builder) {
		PlayerTagComponent tags = PlayerTagComponent.KEY.getNullable(ctx.getSource().getWorld());
		return CommandSource.suggestMatching(tags == null ? List.of() : tags.getCustomTags().keySet(), builder);
	}

	private static CompletableFuture<Suggestions> suggestPermissions(CommandContext<ServerCommandSource> ctx,
			SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(List.of("admin", "host", "setup", "builder", "staff", "trusted", "owner"),
			builder);
	}

	private static int runSet(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return runToggle(ctx, true);
	}

	private static int runToggle(CommandContext<ServerCommandSource> ctx, boolean enabled)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		HostComponent hosts = HostComponent.KEY.get(src.getWorld());
		TrustedComponent trusted = TrustedComponent.KEY.get(src.getWorld());
		PlayerTagComponent tags = PlayerTagComponent.KEY.get(src.getWorld());
		String rawTag = StringArgumentType.getString(ctx, "tag");
		PlayerTag tag = PlayerTag.byId(rawTag);
		PlayerTagComponent.CustomTag customTag = tag == null ? tags.getCustomTag(rawTag) : null;
		if ((tag == null && customTag == null) || tag == PlayerTag.DEV || (tag != null && !tag.assignable())) {
			src.sendError(Text.literal("Use one of: " + String.join(", ", assignableTags(src))));
			return 0;
		}
		Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int changed = 0;
		int skippedDev = 0;
		ServerPlayerEntity sourcePlayer = src.getPlayer();
		for (GameProfile profile : profiles) {
			boolean targetIsSource = sourcePlayer != null && sourcePlayer.getUuid().equals(profile.getId());
			if ((GexpressPermissions.isDevUuid(profile.getId())
					|| GexpressPermissions.isDevName(profile.getName())) && !targetIsSource) {
				skippedDev++;
				continue;
			}
			if (customTag != null ? setCustomTag(profile.getId(), customTag.id(), enabled, tags)
					: setTag(profile.getId(), tag, enabled, hosts, trusted, tags)) {
				changed++;
			}
			ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(profile.getId());
			if (online != null) {
				online.sendMessage(Text.literal("Your G'Express tag was updated: ").formatted(Formatting.GRAY)
					.append(customTag == null ? GexpressPermissions.tagBadge(tag)
						: GexpressPermissions.tagBadge(GexpressPermissions.TagInfo.from(customTag))), false);
				refreshPlayerListName(online);
			}
		}

		final int finalChanged = changed;
		final int finalSkippedDev = skippedDev;
		src.sendFeedback(() -> {
			Text base = Text.literal("Updated " + finalChanged + " player tag(s).").formatted(Formatting.GREEN);
			if (finalSkippedDev > 0) {
				return base.copy().append(Text.literal(" (" + finalSkippedDev + " dev skipped)").formatted(Formatting.GRAY));
			}
			return base;
		}, true);
		return changed;
	}

	private static boolean setTag(UUID uuid, PlayerTag tag, boolean enabled, HostComponent hosts, TrustedComponent trusted,
			PlayerTagComponent tags) {
		if (tag == PlayerTag.PASSENGER) {
			boolean changed = hosts.removeHost(uuid) | trusted.removeTrusted(uuid) | tags.clearTag(uuid);
			return changed;
		}
		if (tag == PlayerTag.HOST) return enabled ? hosts.addHost(uuid) : hosts.removeHost(uuid);
		if (tag == PlayerTag.TRUSTED) return enabled ? trusted.addTrusted(uuid) : trusted.removeTrusted(uuid);
		if (tag == PlayerTag.OWNER || tag == PlayerTag.STAFF || tag == PlayerTag.CREATOR) {
			return enabled ? tags.addTag(uuid, tag) : tags.removeTag(uuid, tag);
		}
		return false;
	}

	private static boolean setCustomTag(UUID uuid, String tag, boolean enabled, PlayerTagComponent tags) {
		return enabled ? tags.addCustomTag(uuid, tag) : tags.removeCustomTag(uuid, tag);
	}

	public static void refreshPlayerListName(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) return;
		PlayerListS2CPacket packet = new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, player);
		for (ServerPlayerEntity viewer : player.getServer().getPlayerManager().getPlayerList()) {
			viewer.networkHandler.sendPacket(packet);
		}
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		List<String> rows = new ArrayList<>();
		for (ServerPlayerEntity player : src.getServer().getPlayerManager().getPlayerList()) {
			List<String> tags = GexpressPermissions.effectiveTagInfos(player).stream()
				.map(GexpressPermissions.TagInfo::displayName).toList();
			rows.add(player.getGameProfile().getName() + "=" + String.join("+", tags));
		}
		if (rows.isEmpty()) {
			src.sendFeedback(() -> Text.literal("No online players.").formatted(Formatting.GRAY), false);
			return 0;
		}
		src.sendFeedback(() -> Text.literal("Online tags: " + String.join(", ", rows)), false);
		return rows.size();
	}

	private static List<String> assignableTags(ServerCommandSource src) {
		List<String> out = new ArrayList<>(ASSIGNABLE_TAGS);
		PlayerTagComponent tags = PlayerTagComponent.KEY.getNullable(src.getWorld());
		if (tags != null) out.addAll(tags.getCustomTags().keySet());
		return out;
	}

	private static int runCreateCustom(CommandContext<ServerCommandSource> ctx) {
		PlayerTagComponent tags = PlayerTagComponent.KEY.get(ctx.getSource().getWorld());
		String id = StringArgumentType.getString(ctx, "id");
		String display = StringArgumentType.getString(ctx, "display").replace('_', ' ');
		int color = parseColor(StringArgumentType.getString(ctx, "color"));
		int priority = IntegerArgumentType.getInteger(ctx, "priority");
		if (color < 0 || !tags.defineCustomTag(id, display, color, priority)) {
			ctx.getSource().sendError(Text.literal("Could not create that tag. Use a unique id and a hex color."));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Text.literal("Created custom tag " + id + "."), true);
		return 1;
	}

	private static int runDeleteCustom(CommandContext<ServerCommandSource> ctx) {
		PlayerTagComponent tags = PlayerTagComponent.KEY.get(ctx.getSource().getWorld());
		String id = StringArgumentType.getString(ctx, "id");
		boolean changed = tags.removeCustomTag(id);
		ctx.getSource().sendFeedback(() -> Text.literal(changed ? "Deleted custom tag " + id + "." : "No tag changed."),
			true);
		return changed ? 1 : 0;
	}

	private static int runCustomName(CommandContext<ServerCommandSource> ctx) {
		PlayerTagComponent tags = PlayerTagComponent.KEY.get(ctx.getSource().getWorld());
		String id = StringArgumentType.getString(ctx, "id");
		String display = StringArgumentType.getString(ctx, "display").replace('_', ' ');
		boolean changed = tags.setCustomTagName(id, display);
		ctx.getSource().sendFeedback(() -> Text.literal(changed ? "Updated tag name." : "No tag changed."), true);
		return changed ? 1 : 0;
	}

	private static int runCustomColor(CommandContext<ServerCommandSource> ctx) {
		PlayerTagComponent tags = PlayerTagComponent.KEY.get(ctx.getSource().getWorld());
		String id = StringArgumentType.getString(ctx, "id");
		int color = parseColor(StringArgumentType.getString(ctx, "color"));
		boolean changed = color >= 0 && tags.setCustomTagColor(id, color);
		ctx.getSource().sendFeedback(() -> Text.literal(changed ? "Updated tag color." : "No tag changed."), true);
		return changed ? 1 : 0;
	}

	private static int runCustomPriority(CommandContext<ServerCommandSource> ctx) {
		PlayerTagComponent tags = PlayerTagComponent.KEY.get(ctx.getSource().getWorld());
		String id = StringArgumentType.getString(ctx, "id");
		int priority = IntegerArgumentType.getInteger(ctx, "priority");
		boolean changed = tags.setCustomTagPriority(id, priority);
		ctx.getSource().sendFeedback(() -> Text.literal(changed ? "Updated tag priority." : "No tag changed."), true);
		return changed ? 1 : 0;
	}

	private static int runCustomPermission(CommandContext<ServerCommandSource> ctx) {
		PlayerTagComponent tags = PlayerTagComponent.KEY.get(ctx.getSource().getWorld());
		String id = StringArgumentType.getString(ctx, "id");
		String permission = StringArgumentType.getString(ctx, "permission");
		boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
		boolean changed = tags.setCustomTagPermission(id, permission, enabled);
		ctx.getSource().sendFeedback(() -> Text.literal(changed ? "Updated tag permission." : "No tag changed."),
			true);
		return changed ? 1 : 0;
	}

	private static int parseColor(String raw) {
		if (raw == null) return -1;
		String cleaned = raw.trim();
		if (cleaned.startsWith("#")) cleaned = cleaned.substring(1);
		if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) cleaned = cleaned.substring(2);
		try {
			int color = Integer.parseUnsignedInt(cleaned, 16);
			return cleaned.length() <= 6 ? color & 0xFFFFFF : -1;
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}
}
