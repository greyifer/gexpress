package dev.mapselect.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mapselect.command.admin.ConfigCommand;
import dev.mapselect.command.admin.DevCommand;
import dev.mapselect.command.admin.DiagnosticsCommand;
import dev.mapselect.command.admin.ForceCommand;
import dev.mapselect.command.admin.GcoinCommand;
import dev.mapselect.command.admin.HostCommand;
import dev.mapselect.command.admin.KeycardCommand;
import dev.mapselect.command.admin.LeaderboardCommand;
import dev.mapselect.command.admin.SkinCommand;
import dev.mapselect.command.admin.TagCommand;
import dev.mapselect.command.admin.TestCommand;
import dev.mapselect.command.admin.TrustedCommand;
import dev.mapselect.command.admin.TuningCommand;
import dev.mapselect.command.admin.VoiceCommand;
import dev.mapselect.command.game.AutoGameCommand;
import dev.mapselect.command.game.EndCommand;
import dev.mapselect.command.game.StartCommand;
import dev.mapselect.command.roles.PelicanCommand;
import dev.mapselect.command.setup.MapCommand;
import dev.mapselect.command.setup.RtpCommand;
import dev.mapselect.command.setup.SetupChecklistCommand;
import dev.mapselect.command.setup.TrainCommand;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public final class GexpressCommand {
	private GexpressCommand() {}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		var commands = dispatcher.register(root("g"));
		dispatcher.register(CommandManager.literal("gexpress").redirect(commands));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> root(String name) {
		return CommandManager.literal(name)
			.then(ReplayCommand.buildTree())
			.then(CommandManager.literal("game")
				.then(StartCommand.buildTree())
				.then(EndCommand.buildTree())
				.then(AutoGameCommand.buildTree()))
			.then(CommandManager.literal("setup")
				.then(SetupChecklistCommand.buildTree())
				.then(MapCommand.buildMapTree())
				.then(TrainCommand.buildTree())
				.then(RtpCommand.buildTree()))
			.then(TestCommand.buildTree())
			.then(LobbyPollCommand.buildTree())
			.then(ServerAccessCommand.openTree())
			.then(ServerAccessCommand.closeTree())
			.then(CommandManager.literal("roles")
				.then(TuningCommand.buildRoleTree())
				.then(PelicanCommand.buildTree()))
			.then(CommandManager.literal("modifiers")
				.then(TuningCommand.buildModifierTree()))
			.then(ForceCommand.buildTree())
			.then(SkinCommand.buildPublicTree())
			.then(DevCommand.appendTo(CommandManager.literal("admin")
				.then(HostCommand.buildTree())
				.then(TrustedCommand.buildTree())
				.then(TagCommand.buildTree())
				.then(DiagnosticsCommand.buildTree())
				.then(ConfigCommand.buildTree())
				.then(KeycardCommand.buildTree())
				.then(LeaderboardCommand.buildTree())
				.then(SkinCommand.buildTree())
				.then(GcoinCommand.buildTree())
				.then(VoiceCommand.buildTree())));
	}
}
