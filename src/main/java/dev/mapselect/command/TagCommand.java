package dev.mapselect.command;

import com.mojang.authlib.GameProfile;
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
			.then(CommandManager.literal("list")
				.executes(TagCommand::runList));
	}

	private static CompletableFuture<Suggestions> suggestTags(CommandContext<ServerCommandSource> ctx,
			SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(ASSIGNABLE_TAGS, builder);
	}

	private static int runSet(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return runToggle(ctx, true);
	}

	private static int runToggle(CommandContext<ServerCommandSource> ctx, boolean enabled)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		PlayerTag tag = PlayerTag.byId(StringArgumentType.getString(ctx, "tag"));
		if (tag == null || !tag.assignable() || tag == PlayerTag.DEV) {
			src.sendError(Text.literal("Use one of: " + String.join(", ", ASSIGNABLE_TAGS)));
			return 0;
		}

		HostComponent hosts = HostComponent.KEY.get(src.getWorld());
		TrustedComponent trusted = TrustedComponent.KEY.get(src.getWorld());
		PlayerTagComponent tags = PlayerTagComponent.KEY.get(src.getWorld());
		Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int changed = 0;
		int skippedDev = 0;
		for (GameProfile profile : profiles) {
			if (GexpressPermissions.isDevUuid(profile.getId())
					|| GexpressPermissions.isDevName(profile.getName())) {
				skippedDev++;
				continue;
			}
			if (setTag(profile.getId(), tag, enabled, hosts, trusted, tags)) {
				changed++;
			}
			ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(profile.getId());
			if (online != null) {
				online.sendMessage(Text.literal("Your G'Express tag was updated: ").formatted(Formatting.GRAY)
					.append(GexpressPermissions.tagBadge(tag)), false);
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
		if (tag == PlayerTag.OWNER || tag == PlayerTag.STAFF) {
			return enabled ? tags.addTag(uuid, tag) : tags.removeTag(uuid, tag);
		}
		return false;
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
			List<String> tags = GexpressPermissions.effectiveTags(player).stream().map(PlayerTag::displayName).toList();
			rows.add(player.getGameProfile().getName() + "=" + String.join("+", tags));
		}
		if (rows.isEmpty()) {
			src.sendFeedback(() -> Text.literal("No online players.").formatted(Formatting.GRAY), false);
			return 0;
		}
		src.sendFeedback(() -> Text.literal("Online tags: " + String.join(", ", rows)), false);
		return rows.size();
	}
}
