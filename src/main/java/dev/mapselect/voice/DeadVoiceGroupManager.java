package dev.mapselect.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import dev.mapselect.MapSelect;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.SpectatorVoiceGroupPayload;
import dev.mapselect.role.pelican.PelicanManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DeadVoiceGroupManager {
	private static final int GROUP_COUNT = 3;
	private static final UUID[] CUSTOM_GROUP_IDS = {
		TrainVoicePlugin.GROUP_ID,
		UUID.nameUUIDFromBytes("gexpress:spectator_voice:2".getBytes(StandardCharsets.UTF_8)),
		UUID.nameUUIDFromBytes("gexpress:spectator_voice:3".getBytes(StandardCharsets.UTF_8))
	};
	private static final Map<UUID, Integer> preferredGroups = new HashMap<>();
	private static int tickDelay;

	private DeadVoiceGroupManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(SpectatorVoiceGroupPayload.ID, SpectatorVoiceGroupPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpectatorVoiceGroupPayload.ID,
			(payload, context) -> context.server().execute(() -> switchGroup(context.player(), payload.delta())));
		ServerTickEvents.END_WORLD_TICK.register(DeadVoiceGroupManager::tick);
	}

	public static boolean canJoinDeadVoice(UUID playerId) {
		ServerPlayerEntity player = playerFor(playerId);
		return player == null || shouldBeInDeadVoice(player);
	}

	public static void handleWatheAddPlayer(UUID playerId) {
		ServerPlayerEntity player = playerFor(playerId);
		if (player == null || !shouldBeInDeadVoice(player) || TrainVoicePlugin.SERVER_API == null) return;
		VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(playerId);
		if (connection != null) applyPreferredGroup(player, connection);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (TrainVoicePlugin.SERVER_API == null || tickDelay++ % 5 != 0) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(player.getUuid());
			if (connection == null) continue;
			boolean inDeadGroup = isManagedGroup(connection.getGroup());
			boolean shouldBeDead = shouldBeInDeadVoice(player);
			if (shouldBeDead) {
				applyPreferredGroup(player, connection);
			} else if (!shouldBeDead && inDeadGroup) {
				connection.setGroup(null);
			}
		}
	}

	private static void switchGroup(ServerPlayerEntity player, int delta) {
		if (player == null || TrainVoicePlugin.SERVER_API == null || delta == 0) return;
		if (!shouldBeInDeadVoice(player)) {
			player.sendMessage(Text.literal("Spectator voice channels are only available while spectating or dead.")
				.formatted(Formatting.GRAY), true);
			return;
		}
		int current = preferredGroup(player.getUuid());
		int next = (current + delta + GROUP_COUNT) % GROUP_COUNT;
		preferredGroups.put(player.getUuid(), next);
		VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(player.getUuid());
		if (connection != null) applyPreferredGroup(player, connection);
		player.sendMessage(Text.literal("Spectator voice: " + groupName(next)).formatted(Formatting.AQUA), true);
	}

	private static void applyPreferredGroup(ServerPlayerEntity player, VoicechatConnection connection) {
		int group = preferredGroup(player.getUuid());
		Group target = ensureCustomGroup(group);
		if (safeGroupId(target) == null) {
			preferredGroups.put(player.getUuid(), 0);
			return;
		}
		if (!sameGroup(connection.getGroup(), target)) {
			safeSetGroup(player, connection, target);
		}
	}

	private static Group ensureCustomGroup(int index) {
		if (TrainVoicePlugin.SERVER_API == null || index < 0 || index >= GROUP_COUNT) return null;
		Group existing = TrainVoicePlugin.SERVER_API.getGroup(CUSTOM_GROUP_IDS[index]);
		if (isExpectedGroup(existing, index)) {
			if (index == 0) TrainVoicePlugin.GROUP = existing;
			return existing;
		}
		try {
			Group group = TrainVoicePlugin.SERVER_API.groupBuilder()
				.setId(CUSTOM_GROUP_IDS[index])
				.setName(groupName(index))
				.setPassword(null)
				.setPersistent(true)
				.setHidden(true)
				.setType(Group.Type.NORMAL)
				.build();
			if (index == 0) TrainVoicePlugin.GROUP = group;
			return group;
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static int preferredGroup(UUID playerId) {
		return Math.max(0, Math.min(GROUP_COUNT - 1, preferredGroups.getOrDefault(playerId, 0)));
	}

	private static String groupName(int index) {
		return "Train Spectators " + (index + 1);
	}

	private static boolean shouldBeInDeadVoice(ServerPlayerEntity player) {
		if (player == null || PelicanManager.isStashed(player)) return false;
		return DeadPlayerStatus.isDeadRoundParticipant(player) || isSpectatorDuringRound(player);
	}

	private static boolean isSpectatorDuringRound(ServerPlayerEntity player) {
		if (player == null || player.getWorld() == null || PelicanManager.isStashed(player)) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		return game != null && game.isRunning() && player.isSpectator()
			&& !DeadPlayerStatus.isLivingRoundParticipant(player);
	}

	private static ServerPlayerEntity playerFor(UUID playerId) {
		if (playerId == null || TrainVoicePlugin.SERVER_API == null) return null;
		VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(playerId);
		if (connection == null || connection.getPlayer() == null) return null;
		Object player = connection.getPlayer().getPlayer();
		return player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
	}

	private static boolean isManagedGroup(Group group) {
		UUID groupId = safeGroupId(group);
		if (groupId == null) return false;
		for (UUID id : CUSTOM_GROUP_IDS) {
			if (id.equals(groupId)) return true;
		}
		return false;
	}

	private static boolean sameGroup(Group first, Group second) {
		UUID firstId = safeGroupId(first);
		UUID secondId = safeGroupId(second);
		return firstId != null && firstId.equals(secondId);
	}

	private static boolean isExpectedGroup(Group group, int index) {
		if (group == null || !CUSTOM_GROUP_IDS[index].equals(safeGroupId(group))) return false;
		try {
			return groupName(index).equals(group.getName())
				&& group.isHidden()
				&& group.isPersistent()
				&& group.getType() == Group.Type.NORMAL;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void safeSetGroup(ServerPlayerEntity player, VoicechatConnection connection, Group target) {
		try {
			connection.setGroup(target);
		} catch (Throwable t) {
			preferredGroups.put(player.getUuid(), 0);
			MapSelect.LOGGER.warn("Failed to move {} into {}.",
				player.getGameProfile().getName(), groupName(preferredGroup(player.getUuid())), t);
		}
	}

	private static UUID safeGroupId(Group group) {
		if (group == null) return null;
		try {
			return group.getId();
		} catch (Throwable ignored) {
			return null;
		}
	}
}
