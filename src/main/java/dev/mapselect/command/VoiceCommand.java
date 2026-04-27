package dev.mapselect.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.voice.VoiceMuteState;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;

public class VoiceCommand {

	private static final Predicate<ServerCommandSource> OP_OR_HOST = GexpressPermissions::canUseHostCommands;

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("voice")
			.requires(OP_OR_HOST)
			.then(CommandManager.literal("mute")
				.then(CommandManager.literal("all")
					.executes(VoiceCommand::runMuteAll))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(VoiceCommand::runMute)))
			.then(CommandManager.literal("unmute")
				.then(CommandManager.literal("all")
					.executes(VoiceCommand::runUnmuteAll))
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(VoiceCommand::runUnmute)))
			.then(CommandManager.literal("list")
				.executes(VoiceCommand::runList));
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
		src.sendFeedback(() -> {
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
		src.sendFeedback(() -> Text.literal("Unmuted " + fn + " player(s).").formatted(Formatting.GREEN), true);
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
		src.sendFeedback(() -> Text.literal("Muted " + fn + " player(s) (privileged excluded).").formatted(Formatting.GREEN), true);
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
		src.sendFeedback(() -> Text.literal("Cleared all " + n + " voice mute(s).").formatted(Formatting.GREEN), true);
		return n;
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		VoiceMuteState st = state(src);
		if (st.getMuted().isEmpty()) {
			src.sendFeedback(() -> Text.literal("No voice mutes.").formatted(Formatting.GRAY), false);
			return 0;
		}
		StringBuilder sb = new StringBuilder();
		for (UUID u : st.getMuted()) {
			String name;
			ServerPlayerEntity online = src.getServer().getPlayerManager().getPlayer(u);
			if (online != null) name = online.getGameProfile().getName();
			else {
				var cache = src.getServer().getUserCache();
				name = cache == null ? u.toString()
					: cache.getByUuid(u).map(pp -> pp.getName()).orElse(u.toString());
			}
			if (sb.length() > 0) sb.append(", ");
			sb.append(name);
		}
		final String joined = sb.toString();
		src.sendFeedback(() -> Text.literal("Voice-muted (" + st.getMuted().size() + "): " + joined), false);
		return st.getMuted().size();
	}
}
