package dev.mapselect.command;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.Supplier;

public final class GexpressFeedback {
	private GexpressFeedback() {}

	public static void send(ServerCommandSource source, Supplier<Text> message, boolean broadcastToOps) {
		if (message != null) send(source, message.get());
	}

	public static void send(ServerCommandSource source, Supplier<Text> message) {
		if (message != null) send(source, message.get());
	}

	public static void send(ServerCommandSource source, Text message) {
		if (source == null || message == null) return;
		if (source.getEntity() instanceof ServerPlayerEntity player) {
			player.sendMessage(message, false);
		} else {
			source.sendMessage(message);
		}
	}
}
