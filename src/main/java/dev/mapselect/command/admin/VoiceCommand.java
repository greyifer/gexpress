package dev.mapselect.command.admin;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mapselect.command.CommandPlayerFormat;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.voice.DeadVoiceGroupManager;
import dev.mapselect.voice.VoiceMuteState;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class VoiceCommand {

	private static final Predicate<ServerCommandSource> OP_OR_HOST = source ->
		GexpressPermissions.canManageVoice(source)
			|| GexpressPermissions.canUseCommandBranch(source, "admin", "voice");

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("voice")
			.requires(OP_OR_HOST)
			.then(CommandManager.literal("mute")
				.requires(source -> canUseVoiceCommand(source, "mute"))
				.then(CommandManager.literal("all")
					.executes(VoiceCommand::runMuteAll))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(VoiceCommand::runMute)))
			.then(CommandManager.literal("unmute")
				.requires(source -> canUseVoiceCommand(source, "unmute"))
				.then(CommandManager.literal("all")
					.executes(VoiceCommand::runUnmuteAll))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(VoiceCommand::runUnmute)))
			.then(CommandManager.literal("dead")
				.requires(source -> canUseVoiceCommand(source, "dead"))
				.then(CommandManager.literal("groups")
					.executes(VoiceCommand::runDeadGroupsList)
					.then(CommandManager.literal("list")
						.executes(VoiceCommand::runDeadGroupsList))
					.then(CommandManager.literal("add")
						.executes(ctx -> runDeadGroupsAdd(ctx, 1))
						.then(CommandManager.argument("amount", IntegerArgumentType.integer(1, VoiceMuteState.MAX_DEAD_VOICE_GROUP_COUNT))
							.executes(ctx -> runDeadGroupsAdd(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
					.then(CommandManager.literal("remove")
						.executes(ctx -> runDeadGroupsRemove(ctx, 1))
						.then(CommandManager.argument("amount", IntegerArgumentType.integer(1, VoiceMuteState.MAX_DEAD_VOICE_GROUP_COUNT))
							.executes(ctx -> runDeadGroupsRemove(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
					.then(CommandManager.literal("set")
						.then(CommandManager.argument("count", IntegerArgumentType.integer(
								VoiceMuteState.MIN_DEAD_VOICE_GROUP_COUNT,
								VoiceMuteState.MAX_DEAD_VOICE_GROUP_COUNT))
							.executes(VoiceCommand::runDeadGroupsSet))))
				.then(CommandManager.literal("move")
					.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
						.then(CommandManager.argument("group", IntegerArgumentType.integer(1, VoiceMuteState.MAX_DEAD_VOICE_GROUP_COUNT))
							.executes(VoiceCommand::runDeadMove))))
				.then(CommandManager.literal("admin")
					.then(CommandManager.literal("add")
						.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
							.executes(VoiceCommand::runDeadAdminAdd)))
					.then(CommandManager.literal("remove")
						.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
							.executes(VoiceCommand::runDeadAdminRemove)))
					.then(CommandManager.literal("clear")
						.executes(VoiceCommand::runDeadAdminClear))
					.then(CommandManager.literal("list")
							.executes(VoiceCommand::runDeadAdminList))))
			.then(CommandManager.literal("list")
				.requires(source -> canUseVoiceCommand(source, "list"))
				.executes(VoiceCommand::runList))
			.then(CommandManager.literal("status")
				.requires(source -> canUseVoiceCommand(source, "status"))
				.executes(VoiceCommand::runStatusSummary)
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(VoiceCommand::runStatus)));
	}

	private static boolean canUseVoiceCommand(ServerCommandSource source, String subcommand) {
		return GexpressPermissions.canManageVoice(source)
			|| GexpressPermissions.canUseCommandPath(source, "admin", "voice", subcommand);
	}

	private static VoiceMuteState state(ServerCommandSource src) {
		return VoiceMuteState.KEY.get(src.getWorld());
	}

	private static boolean isOp(MinecraftServer server, UUID uuid) {
		return GexpressPermissions.isOperatorLike(server, uuid);
	}

	private static int runMute(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		VoiceMuteState st = state(src);
		Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int muted = 0;
		int skippedPrivileged = 0;
		for (com.mojang.authlib.GameProfile p : profiles) {
			if (isOp(src.getServer(), p.getId())) {
				skippedPrivileged++;
				continue;
			}
			if (st.mute(p.getId())) {
				muted++;
				ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(p.getId());
				if (online != null) {
					online.sendMessage(Text.literal("You have been voice-muted.").formatted(Formatting.RED), false);
				}
			}
		}
		final int fm = muted;
		final int fs = skippedPrivileged;
		dev.mapselect.command.GexpressFeedback.send(src, () -> {
			Text base = Text.literal("Muted " + fm + " player(s).").formatted(Formatting.GREEN);
			if (fs > 0) return base.copy().append(Text.literal(" (" + fs + " privileged skipped)").formatted(Formatting.GRAY));
			return base;
		}, true);
		return muted;
	}

	private static int runUnmute(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		VoiceMuteState st = state(src);
		Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int n = 0;
		for (com.mojang.authlib.GameProfile p : profiles) {
			if (st.unmute(p.getId())) {
				n++;
				ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(p.getId());
				if (online != null) {
					online.sendMessage(Text.literal("You have been voice-unmuted.").formatted(Formatting.GREEN), false);
				}
			}
		}
		final int fn = n;
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Unmuted " + fn + " player(s).").formatted(Formatting.GREEN), true);
		return n;
	}

	private static int runMuteAll(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		VoiceMuteState st = state(src);
		int n = 0;
		for (ServerPlayerEntity p : src.getServer().getPlayerManager().getPlayerList()) {
			if (GexpressPermissions.canEditGameOptions(p)) continue;
			if (st.mute(p.getUuid())) {
				n++;
				p.sendMessage(Text.literal("You have been voice-muted.").formatted(Formatting.RED), false);
			}
		}
		final int fn = n;
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Muted " + fn + " player(s) (privileged excluded).").formatted(Formatting.GREEN), true);
		return n;
	}

	private static int runUnmuteAll(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		VoiceMuteState st = state(src);
		int n = st.getMuted().size();
		st.clear();
		for (ServerPlayerEntity p : src.getServer().getPlayerManager().getPlayerList()) {
			p.sendMessage(Text.literal("Voice mutes cleared.").formatted(Formatting.GREEN), false);
		}
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Cleared all " + n + " voice mute(s).").formatted(Formatting.GREEN), true);
		return n;
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		VoiceMuteState st = state(src);
		if (st.getMuted().isEmpty()) {
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("No voice mutes.").formatted(Formatting.GRAY), false);
			return 0;
		}
		StringBuilder sb = new StringBuilder();
		for (UUID u : st.getMuted()) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(CommandPlayerFormat.cachedName(src, u));
		}
		final String joined = sb.toString();
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Voice-muted (" + st.getMuted().size() + "): " + joined), false);
		return st.getMuted().size();
	}

	private static int runDeadGroupsList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		int groups = DeadVoiceGroupManager.normalGroupCount(src.getWorld());
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Dead voice groups: " + groups
			+ " normal group(s) plus the admin group.").formatted(Formatting.AQUA), false);
		return groups;
	}

	private static int runDeadGroupsAdd(CommandContext<ServerCommandSource> ctx, int amount) {
		ServerCommandSource src = ctx.getSource();
		int before = DeadVoiceGroupManager.normalGroupCount(src.getWorld());
		int after = DeadVoiceGroupManager.addNormalGroups(src.getWorld(), amount);
		int added = Math.max(0, after - before);
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Dead voice groups: " + before + " -> " + after
			+ " normal group(s). Admin group is unchanged.").formatted(Formatting.GREEN), true);
		return added;
	}

	private static int runDeadGroupsRemove(CommandContext<ServerCommandSource> ctx, int amount) {
		ServerCommandSource src = ctx.getSource();
		int before = DeadVoiceGroupManager.normalGroupCount(src.getWorld());
		int after = DeadVoiceGroupManager.removeNormalGroups(src.getWorld(), amount);
		int removed = Math.max(0, before - after);
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Dead voice groups: " + before + " -> " + after
			+ " normal group(s). Admin group is unchanged.").formatted(Formatting.GREEN), true);
		return removed;
	}

	private static int runDeadGroupsSet(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		int requested = IntegerArgumentType.getInteger(ctx, "count");
		int after = DeadVoiceGroupManager.setNormalGroupCount(src.getWorld(), requested);
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Dead voice groups set to " + after
			+ " normal group(s) plus the admin group.").formatted(Formatting.GREEN), true);
		return after;
	}

	private static int runDeadMove(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		int group = IntegerArgumentType.getInteger(ctx, "group");
		int available = DeadVoiceGroupManager.normalGroupCount(src.getWorld());
		if (group > available) {
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Dead voice group " + group + " does not exist. Current normal groups: "
				+ available + ".").formatted(Formatting.RED), false);
			return 0;
		}
		Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int moved = 0;
		for (GameProfile profile : profiles) {
			if (DeadVoiceGroupManager.moveToNormalGroup(src.getWorld(), profile.getId(), group)) moved++;
		}
		final int fm = moved;
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Moved " + fm + " player(s) to dead voice group " + group + ".")
			.formatted(Formatting.GREEN), true);
		return moved;
	}

	private static int runDeadAdminAdd(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int changed = 0;
		int applied = 0;
		for (GameProfile profile : profiles) {
			if (DeadVoiceGroupManager.lockAdminGroup(src.getWorld(), profile.getId())) changed++;
			applied++;
		}
		final int fa = applied;
		final int fc = changed;
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Admin dead voice applied to " + fa + " player(s) ("
			+ fc + " new lock(s)).").formatted(Formatting.GREEN), true);
		return applied;
	}

	private static int runDeadAdminRemove(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int removed = 0;
		for (GameProfile profile : profiles) {
			if (DeadVoiceGroupManager.unlockAdminGroup(src.getWorld(), profile.getId())) removed++;
		}
		final int fr = removed;
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Removed admin dead voice lock from " + fr + " player(s).")
			.formatted(Formatting.GREEN), true);
		return removed;
	}

	private static int runDeadAdminClear(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		int cleared = DeadVoiceGroupManager.clearAdminLocks(src.getWorld());
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Cleared " + cleared + " admin dead voice lock(s).")
			.formatted(Formatting.GREEN), true);
		return cleared;
	}

	private static int runDeadAdminList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		Set<UUID> locked = DeadVoiceGroupManager.adminLockedPlayers(src.getWorld());
		if (locked.isEmpty()) {
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("No admin dead voice locks.").formatted(Formatting.GRAY), false);
			return 0;
		}
		StringBuilder sb = new StringBuilder();
		for (UUID uuid : locked) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(CommandPlayerFormat.cachedName(src, uuid));
		}
		final int count = locked.size();
		final String joined = sb.toString();
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Admin dead voice locked (" + count + "): " + joined), false);
		return count;
	}

	private static int runStatusSummary(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		VoiceMuteState st = state(src);
		int muted = st.getMuted().size();
		int adminLocks = DeadVoiceGroupManager.adminLockedPlayers(src.getWorld()).size();
		int groups = DeadVoiceGroupManager.normalGroupCount(src.getWorld());
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Voice status: muted=" + muted
			+ ", deadGroups=" + groups + ", adminLocks=" + adminLocks
			+ ". Use /g admin voice status <players> for connection details.").formatted(Formatting.AQUA), false);
		return muted + adminLocks + groups;
	}

	private static int runStatus(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = ctx.getSource();
		Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		int shown = 0;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			ServerPlayerEntity player = src.getServer().getPlayerManager().getPlayer(profile.getId());
			String status = player == null ? "offline" : DeadVoiceGroupManager.status(player);
			String name = player == null ? profile.getName() : player.getName().getString();
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(name + ": " + status)
				.formatted(player == null ? Formatting.GRAY : Formatting.WHITE), false);
			shown++;
		}
		return shown;
	}
}
