package dev.mapselect.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class GreyCommand {
	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("g")
			.then(CommandManager.literal("game")
				.then(StartCommand.buildTree())
				.then(EndCommand.buildTree()))
			.then(CommandManager.literal("setup")
				.then(MapSelectCommand.buildMapTree())
				.then(TrainCommand.buildTree())
				.then(RtpCommand.buildTree()))
			.then(CommandManager.literal("roles")
				.then(TestCommand.buildTree())
				.then(TuningCommand.buildTree())
				.then(VultureCommand.buildTree())
				.then(C4Command.buildTree()))
			.then(CommandManager.literal("group")
				.then(HostCommand.buildTree())
				.then(TrustedCommand.buildTree())
				.then(TagCommand.buildTree())
				.then(VoiceCommand.buildTree()))
			.then(CommandManager.literal("admin")
				.then(DevCommand.buildTree())));
	}
}
