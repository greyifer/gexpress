package dev.mapselect.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import dev.mapselect.game.DeadPlayerStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.UUID;

public final class DeadVoiceGroupManager {
	private static int tickDelay;

	private DeadVoiceGroupManager() {}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(DeadVoiceGroupManager::tick);
	}

	public static boolean canJoinDeadVoice(UUID playerId) {
		ServerPlayerEntity player = playerFor(playerId);
		return player == null || DeadPlayerStatus.isDeadRoundParticipant(player);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (TrainVoicePlugin.SERVER_API == null || tickDelay++ % 5 != 0) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(player.getUuid());
			if (connection == null || !isWatheDeadGroup(connection.getGroup())) continue;
			if (!DeadPlayerStatus.isDeadRoundParticipant(player)) {
				connection.setGroup(null);
			}
		}
	}

	private static ServerPlayerEntity playerFor(UUID playerId) {
		if (playerId == null || TrainVoicePlugin.SERVER_API == null) return null;
		VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(playerId);
		if (connection == null || connection.getPlayer() == null) return null;
		Object player = connection.getPlayer().getPlayer();
		return player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
	}

	private static boolean isWatheDeadGroup(Group group) {
		return group != null && TrainVoicePlugin.GROUP_ID.equals(group.getId());
	}
}
