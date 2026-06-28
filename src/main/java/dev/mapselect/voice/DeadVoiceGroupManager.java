package dev.mapselect.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import dev.mapselect.MapSelect;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.voice.SpectatorVoiceGroupPayload;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DeadVoiceGroupManager {
	private static final UUID ADMIN_GROUP_ID = UUID.nameUUIDFromBytes(
		"gexpress:spectator_voice:admin".getBytes(StandardCharsets.UTF_8)
	);
	private static final Map<UUID, Integer> preferredGroups = new HashMap<>();
	private static final Set<UUID> activeDeadVoicePlayers = new HashSet<>();
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
		markDeadVoiceEntry(player);
		VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(playerId);
		if (connection != null) applyPreferredGroup(player, connection);
	}

	public static int normalGroupCount(ServerWorld world) {
		VoiceMuteState state = world == null ? null : VoiceMuteState.KEY.getNullable(world);
		return state == null ? VoiceMuteState.DEFAULT_DEAD_VOICE_GROUP_COUNT : state.getDeadVoiceGroupCount();
	}

	public static int setNormalGroupCount(ServerWorld world, int count) {
		if (world == null) return VoiceMuteState.clampDeadVoiceGroupCount(count);
		VoiceMuteState state = VoiceMuteState.KEY.get(world);
		int previous = state.getDeadVoiceGroupCount();
		int updated = state.setDeadVoiceGroupCount(count);
		if (updated != previous) {
			VoiceMuteState.KEY.sync(world);
			clampPreferredGroups(updated);
			ensureConfiguredGroups(world);
			reapplyOnlineDeadVoice(world);
		}
		return updated;
	}

	public static int addNormalGroups(ServerWorld world, int amount) {
		if (world == null) return VoiceMuteState.DEFAULT_DEAD_VOICE_GROUP_COUNT;
		VoiceMuteState state = VoiceMuteState.KEY.get(world);
		return setNormalGroupCount(world, state.getDeadVoiceGroupCount() + Math.max(0, amount));
	}

	public static int removeNormalGroups(ServerWorld world, int amount) {
		if (world == null) return VoiceMuteState.DEFAULT_DEAD_VOICE_GROUP_COUNT;
		VoiceMuteState state = VoiceMuteState.KEY.get(world);
		return setNormalGroupCount(world, state.getDeadVoiceGroupCount() - Math.max(0, amount));
	}

	public static boolean moveToNormalGroup(ServerWorld world, UUID playerId, int oneBasedGroup) {
		if (world == null || playerId == null) return false;
		int groupCount = normalGroupCount(world);
		if (oneBasedGroup < 1 || oneBasedGroup > groupCount) return false;
		VoiceMuteState state = VoiceMuteState.KEY.get(world);
		state.unlockAdminDeadVoice(playerId);
		preferredGroups.put(playerId, oneBasedGroup - 1);
		ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
		if (player != null && shouldBeInDeadVoice(player)) activeDeadVoicePlayers.add(playerId);
		VoiceMuteState.KEY.sync(world);
		applyIfOnlineAndDead(world, playerId);
		return true;
	}

	public static boolean lockAdminGroup(ServerWorld world, UUID playerId) {
		if (world == null || playerId == null) return false;
		VoiceMuteState state = VoiceMuteState.KEY.get(world);
		boolean changed = state.lockAdminDeadVoice(playerId);
		if (changed) VoiceMuteState.KEY.sync(world);
		applyIfOnlineAndDead(world, playerId);
		return changed;
	}

	public static boolean unlockAdminGroup(ServerWorld world, UUID playerId) {
		if (world == null || playerId == null) return false;
		VoiceMuteState state = VoiceMuteState.KEY.get(world);
		boolean changed = state.unlockAdminDeadVoice(playerId);
		preferredGroups.put(playerId, 0);
		if (changed) VoiceMuteState.KEY.sync(world);
		applyIfOnlineAndDead(world, playerId);
		return changed;
	}

	public static int clearAdminLocks(ServerWorld world) {
		if (world == null) return 0;
		VoiceMuteState state = VoiceMuteState.KEY.get(world);
		Set<UUID> locked = state.getAdminDeadVoiceLocked();
		int cleared = state.clearAdminDeadVoiceLocks();
		if (cleared > 0) {
			VoiceMuteState.KEY.sync(world);
			for (UUID playerId : locked) {
				preferredGroups.put(playerId, 0);
				applyIfOnlineAndDead(world, playerId);
			}
		}
		return cleared;
	}

	public static Set<UUID> adminLockedPlayers(ServerWorld world) {
		VoiceMuteState state = world == null ? null : VoiceMuteState.KEY.getNullable(world);
		return state == null ? Collections.emptySet() : state.getAdminDeadVoiceLocked();
	}

	public static boolean isAdminLocked(ServerPlayerEntity player) {
		if (player == null) return false;
		VoiceMuteState state = VoiceMuteState.KEY.getNullable(player.getServerWorld());
		return state != null && state.isAdminDeadVoiceLocked(player.getUuid());
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (TrainVoicePlugin.SERVER_API == null || tickDelay++ % 5 != 0) return;
		ensureConfiguredGroups(world);
		for (ServerPlayerEntity player : world.getPlayers()) {
			boolean shouldBeDead = shouldBeInDeadVoice(player);
			if (shouldBeDead) markDeadVoiceEntry(player);
			else activeDeadVoicePlayers.remove(player.getUuid());

			VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(player.getUuid());
			if (connection == null) continue;
			boolean inDeadGroup = isManagedGroup(connection.getGroup());
			if (shouldBeDead) {
				applyPreferredGroup(player, connection);
			} else {
				if (inDeadGroup) {
					connection.setGroup(null);
				}
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
		markDeadVoiceEntry(player);
		if (isAdminLocked(player)) {
			VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(player.getUuid());
			if (connection != null) applyPreferredGroup(player, connection);
			player.sendMessage(Text.literal("You are locked in the admin spectator voice channel.")
				.formatted(Formatting.RED), true);
			return;
		}

		int groupCount = normalGroupCount(player.getServerWorld());
		int current = preferredGroup(player.getUuid(), groupCount);
		int next = (current + delta + groupCount) % groupCount;
		preferredGroups.put(player.getUuid(), next);
		VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(player.getUuid());
		if (connection != null) applyPreferredGroup(player, connection);
		player.sendMessage(Text.literal("Spectator voice: " + groupName(next)).formatted(Formatting.AQUA), true);
	}

	private static void applyPreferredGroup(ServerPlayerEntity player, VoicechatConnection connection) {
		Group target;
		if (isAdminLocked(player)) {
			target = ensureAdminGroup();
		} else {
			int groupCount = normalGroupCount(player.getServerWorld());
			target = ensureNormalGroup(preferredGroup(player.getUuid(), groupCount));
		}
		if (safeGroupId(target) == null) {
			preferredGroups.put(player.getUuid(), 0);
			return;
		}
		if (!sameGroup(connection.getGroup(), target)) {
			safeSetGroup(player, connection, target);
		}
	}

	private static void markDeadVoiceEntry(ServerPlayerEntity player) {
		if (player == null) return;
		UUID playerId = player.getUuid();
		if (activeDeadVoicePlayers.add(playerId) && !isAdminLocked(player)) {
			preferredGroups.put(playerId, 0);
		}
	}

	private static void applyIfOnlineAndDead(ServerWorld world, UUID playerId) {
		if (world == null || playerId == null || TrainVoicePlugin.SERVER_API == null) return;
		ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
		if (player == null || !shouldBeInDeadVoice(player)) return;
		markDeadVoiceEntry(player);
		VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(playerId);
		if (connection != null) applyPreferredGroup(player, connection);
	}

	private static void reapplyOnlineDeadVoice(ServerWorld world) {
		if (world == null || TrainVoicePlugin.SERVER_API == null) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!shouldBeInDeadVoice(player)) continue;
			VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(player.getUuid());
			if (connection != null) applyPreferredGroup(player, connection);
		}
	}

	private static void ensureConfiguredGroups(ServerWorld world) {
		if (TrainVoicePlugin.SERVER_API == null) return;
		int groupCount = normalGroupCount(world);
		for (int i = 0; i < groupCount; i++) {
			ensureNormalGroup(i);
		}
		ensureAdminGroup();
	}

	private static Group ensureNormalGroup(int index) {
		if (TrainVoicePlugin.SERVER_API == null || index < 0 || index >= VoiceMuteState.MAX_DEAD_VOICE_GROUP_COUNT) return null;
		UUID groupId = normalGroupId(index);
		Group existing = TrainVoicePlugin.SERVER_API.getGroup(groupId);
		if (isExpectedNormalGroup(existing, index)) {
			if (index == 0) TrainVoicePlugin.GROUP = existing;
			return existing;
		}
		try {
			Group group = TrainVoicePlugin.SERVER_API.groupBuilder()
				.setId(groupId)
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

	private static Group ensureAdminGroup() {
		if (TrainVoicePlugin.SERVER_API == null) return null;
		Group existing = TrainVoicePlugin.SERVER_API.getGroup(ADMIN_GROUP_ID);
		if (isExpectedAdminGroup(existing)) return existing;
		try {
			return TrainVoicePlugin.SERVER_API.groupBuilder()
				.setId(ADMIN_GROUP_ID)
				.setName(adminGroupName())
				.setPassword(null)
				.setPersistent(true)
				.setHidden(true)
				.setType(Group.Type.NORMAL)
				.build();
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static UUID normalGroupId(int index) {
		if (index == 0) return TrainVoicePlugin.GROUP_ID;
		return UUID.nameUUIDFromBytes(("gexpress:spectator_voice:" + (index + 1)).getBytes(StandardCharsets.UTF_8));
	}

	private static int preferredGroup(UUID playerId, int groupCount) {
		int clampedGroupCount = Math.max(1, groupCount);
		int preferred = preferredGroups.getOrDefault(playerId, 0);
		int clamped = Math.max(0, Math.min(clampedGroupCount - 1, preferred));
		if (preferred != clamped) preferredGroups.put(playerId, clamped);
		return clamped;
	}

	private static void clampPreferredGroups(int groupCount) {
		if (groupCount <= 0) return;
		for (Map.Entry<UUID, Integer> entry : preferredGroups.entrySet()) {
			entry.setValue(Math.max(0, Math.min(groupCount - 1, entry.getValue())));
		}
	}

	private static String groupName(int index) {
		return "Train Spectators " + (index + 1);
	}

	private static String adminGroupName() {
		return "Train Spectators Admin";
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
		if (ADMIN_GROUP_ID.equals(groupId)) return true;
		for (int i = 0; i < VoiceMuteState.MAX_DEAD_VOICE_GROUP_COUNT; i++) {
			if (normalGroupId(i).equals(groupId)) return true;
		}
		return false;
	}

	private static boolean sameGroup(Group first, Group second) {
		UUID firstId = safeGroupId(first);
		UUID secondId = safeGroupId(second);
		return firstId != null && firstId.equals(secondId);
	}

	private static boolean isExpectedNormalGroup(Group group, int index) {
		if (group == null || !normalGroupId(index).equals(safeGroupId(group))) return false;
		try {
			return groupName(index).equals(group.getName())
				&& group.isHidden()
				&& group.isPersistent()
				&& group.getType() == Group.Type.NORMAL;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean isExpectedAdminGroup(Group group) {
		if (group == null || !ADMIN_GROUP_ID.equals(safeGroupId(group))) return false;
		try {
			return adminGroupName().equals(group.getName())
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
				player.getGameProfile().getName(), target == null ? "spectator voice" : target.getName(), t);
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
