package dev.mapselect.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class GreyCommand {
	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("g")
			.then(MapSelectCommand.buildMapTree())
			.then(TrainCommand.buildTree())
			.then(RtpCommand.buildTree())
			.then(StartCommand.buildTree())
			.then(EndCommand.buildTree())
			.then(HostCommand.buildTree())
			.then(VoiceCommand.buildTree())
			.then(TestCommand.buildTree())
			.then(C4Command.buildTree())
			.then(TuningCommand.buildTree()));
	}
}
