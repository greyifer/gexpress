package dev.mapselect.server;

import dev.mapselect.host.PlayerTag;
import dev.mapselect.permissions.GexpressPermissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

public final class ServerAccessManager {
	private static final Text CLOSED_MESSAGE = Text.literal("This server is currently closed. Only Host and up can join.")
		.formatted(Formatting.RED);

	private ServerAccessManager() {}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> enforceJoin(handler.player)));
	}

	public static boolean isOpen(MinecraftServer server) {
		ServerAccessComponent access = component(server);
		return access == null || access.isOpen();
	}

	public static boolean shouldSuppressJoinLeaveMessages(MinecraftServer server) {
		return server != null && !isOpen(server);
	}

	public static boolean setOpen(MinecraftServer server, boolean open) {
		ServerAccessComponent access = component(server);
		return access != null && access.setOpen(open);
	}

	public static int kickBelowHost(MinecraftServer server) {
		int kicked = 0;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (canJoinWhenClosed(player)) continue;
			player.networkHandler.disconnect(CLOSED_MESSAGE);
			kicked++;
		}
		return kicked;
	}

	public static boolean canJoinWhenClosed(ServerPlayerEntity player) {
		if (player == null) return false;
		if (player.hasPermissionLevel(2) || GexpressPermissions.isDev(player)) return true;
		return GexpressPermissions.effectiveTagInfos(player).stream()
			.anyMatch(tag -> tag.priority() >= PlayerTag.HOST.priority());
	}

	private static void enforceJoin(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null || isOpen(player.getServer()) || canJoinWhenClosed(player)) {
			return;
		}
		player.networkHandler.disconnect(CLOSED_MESSAGE);
	}

	private static ServerAccessComponent component(MinecraftServer server) {
		if (server == null) return null;
		World world = server.getWorld(World.OVERWORLD);
		return world == null ? null : ServerAccessComponent.KEY.getNullable(world);
	}
}
