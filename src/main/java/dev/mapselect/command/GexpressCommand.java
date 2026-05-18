package dev.mapselect.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mapselect.command.admin.DevCommand;
import dev.mapselect.command.admin.HostCommand;
import dev.mapselect.command.admin.SkinCommand;
import dev.mapselect.command.admin.TagCommand;
import dev.mapselect.command.admin.TestCommand;
import dev.mapselect.command.admin.TrustedCommand;
import dev.mapselect.command.admin.TuningCommand;
import dev.mapselect.command.admin.VoiceCommand;
import dev.mapselect.command.game.EndCommand;
import dev.mapselect.command.game.StartCommand;
import dev.mapselect.command.roles.C4Command;
import dev.mapselect.command.roles.PelicanCommand;
import dev.mapselect.command.setup.MapCommand;
import dev.mapselect.command.setup.RtpCommand;
import dev.mapselect.command.setup.TrainCommand;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public final class GexpressCommand {
	private GexpressCommand() {}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(root("g"));
		dispatcher.register(root("gexpress"));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> root(String name) {
		return CommandManager.literal(name)
			.then(CommandManager.literal("game")
				.then(StartCommand.buildTree())
				.then(EndCommand.buildTree()))
			.then(CommandManager.literal("setup")
				.then(MapCommand.buildMapTree())
				.then(TrainCommand.buildTree())
				.then(RtpCommand.buildTree()))
			.then(CommandManager.literal("roles")
				.then(TuningCommand.buildRoleTree())
				.then(TestCommand.buildRoleTestSubTree())
				.then(PelicanCommand.buildTree())
				.then(C4Command.buildTree()))
			.then(CommandManager.literal("modifiers")
				.then(TuningCommand.buildModifierTree())
				.then(TestCommand.buildModifierTestSubTree()))
			.then(DevCommand.appendTo(CommandManager.literal("admin")
				.then(HostCommand.buildTree())
				.then(TrustedCommand.buildTree())
				.then(TagCommand.buildTree())
				.then(SkinCommand.buildTree())
				.then(VoiceCommand.buildTree())
				.then(TestCommand.buildRoleTestTree())
				.then(TestCommand.buildModifierTestTree())
				.then(TestCommand.buildTaskTestTree())
				.then(DevCommand.buildTree())));
	}
}
