package dev.mapselect.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import dev.mapselect.modifier.ModifierUtils;
import dev.mapselect.game.draft.DraftManager;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.role.harlequin.HarlequinManager;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.role.twins.TwinsManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GreysVoicechatPlugin implements VoicechatPlugin {
	private Method clientPitchForMethod;
	private boolean lookedUpClientPitchForMethod;

	@Override
	public String getPluginId() {
		return "gexpress";
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(de.maxhenkel.voicechat.api.events.ClientVoicechatInitializationEvent.class,
			event -> dev.mapselect.client.replay.ReplayVoicePlayback.initialize(event.getVoicechat()));
		try {
			registration.registerEvent(OpenALSoundEvent.Post.class, this::onOpenALSound);
		} catch (Throwable ignored) {
		}
		try {
			registration.registerEvent(ClientReceiveSoundEvent.class, this::onClientReceiveSound);
		} catch (Throwable ignored) {
		}
		registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
		registration.registerEvent(LocationalSoundPacketEvent.class, this::onSoundPacket);
		registration.registerEvent(EntitySoundPacketEvent.class, this::onSoundPacket);
		registration.registerEvent(StaticSoundPacketEvent.class, this::onSoundPacket);
		registration.registerEvent(CreateGroupEvent.class, this::onCreateGroup);
	}

	private void onMicrophonePacket(MicrophonePacketEvent event) {
		VoicechatConnection sender = event.getSenderConnection();
		if (sender == null) return;
		UUID senderId = sender.getPlayer().getUuid();
		Object level = sender.getPlayer().getServerLevel().getServerLevel();
		if (!(level instanceof ServerWorld world)) return;
		Object nativePlayer = sender.getPlayer().getPlayer();
		if (nativePlayer instanceof ServerPlayerEntity viewer && dev.mapselect.replay.ReplayManager.isViewing(viewer)) {
			event.cancel();
			return;
		}
		if (DraftManager.isDrafting(world)) {
			event.cancel();
			return;
		}
		if (HarlequinManager.isMasqueradeMuted(world, senderId)) {
			event.cancel();
			return;
		}
		VoiceMuteState state = VoiceMuteState.KEY.getNullable(world);
		if (state != null && state.isMuted(senderId)) {
			event.cancel();
			return;
		}
		if (nativePlayer instanceof ServerPlayerEntity player
				&& ModifierUtils.has(player, MapSelectModifiers.MUTED_ID)) {
			event.cancel();
			return;
		}
        ServerPlayerEntity voiceBody = nativePlayer instanceof ServerPlayerEntity player
            ? dev.mapselect.role.loki.LokiManager.voiceSource(player) : null;
        UUID recordedSpeaker = voiceBody == null ? senderId : voiceBody.getUuid();
		dev.mapselect.replay.ReplayRecorder.voice(world, recordedSpeaker, event.getPacket().getOpusEncodedData(),
            (float)(event.getPacket().isWhispering()
                ? event.getVoicechat().getServerConfig().getDouble("whisper_distance", event.getVoicechat().getVoiceChatDistance() / 2)
                : event.getVoicechat().getVoiceChatDistance()));
		if (nativePlayer instanceof ServerPlayerEntity player) {
			Set<UUID> relayReceivers = new HashSet<>(
				TwinsManager.voiceReceiversNear(world, senderId, voiceBody == null ? player.getPos() : voiceBody.getPos()));
			relayReceivers.addAll(SpyManager.bugVoiceReceivers(world, senderId, voiceBody == null ? player.getPos() : voiceBody.getPos()));
			if (!relayReceivers.isEmpty()) {
				StaticSoundPacket packet = event.getPacket().staticSoundPacketBuilder()
					.channelId(senderId)
					.opusEncodedData(event.getPacket().getOpusEncodedData())
					.build();
				for (UUID receiverId : relayReceivers) {
					VoicechatConnection connection = event.getVoicechat().getConnectionOf(receiverId);
					if (connection != null) event.getVoicechat().sendStaticSoundPacketTo(connection, packet);
				}
			}
		}
		Set<UUID> bellyReceivers = PelicanManager.bellyVoiceReceivers(senderId);
		if (!bellyReceivers.isEmpty()) {
			StaticSoundPacket packet = event.getPacket().staticSoundPacketBuilder()
				.channelId(senderId)
				.opusEncodedData(event.getPacket().getOpusEncodedData())
				.build();
			for (UUID receiverId : bellyReceivers) {
				VoicechatConnection connection = event.getVoicechat().getConnectionOf(receiverId);
				if (connection != null) event.getVoicechat().sendStaticSoundPacketTo(connection, packet);
			}
			if (PelicanManager.isStashed(senderId)) {
				event.cancel();
			}
		}
        if (voiceBody != null && !voiceBody.getUuid().equals(senderId) && !event.isCancelled()) {
            float distance = (float)(event.getPacket().isWhispering()
                ? event.getVoicechat().getServerConfig().getDouble("whisper_distance", event.getVoicechat().getVoiceChatDistance()/2)
                : event.getVoicechat().getVoiceChatDistance());
            var packet = event.getPacket().entitySoundPacketBuilder().channelId(senderId)
                .entityUuid(voiceBody.getUuid()).distance(distance).whispering(event.getPacket().isWhispering()).build();
            var position = event.getVoicechat().createPosition(voiceBody.getX(), voiceBody.getEyeY(), voiceBody.getZ());
            for (var listener : event.getVoicechat().getPlayersInRange(event.getVoicechat().fromServerLevel(world), position,
                    Math.max(distance, event.getVoicechat().getBroadcastRange()))) {
                if (listener.getUuid().equals(senderId)) continue;
                var connection = event.getVoicechat().getConnectionOf(listener.getUuid());
                if (connection != null) event.getVoicechat().sendEntitySoundPacketTo(connection, packet);
            }
            // Suppress normal proximity/group routing to avoid a second copy at the original body.
            event.cancel();
        }

	}

	private void onSoundPacket(SoundPacketEvent<?> event) {
		VoicechatConnection sender = event.getSenderConnection();
		VoicechatConnection receiver = event.getReceiverConnection();
		if (sender == null || receiver == null) return;
		if (receiver.getPlayer().getPlayer() instanceof ServerPlayerEntity viewer && dev.mapselect.replay.ReplayManager.isViewing(viewer)) {
			event.cancel();
			return;
		}
		if (isDrafting(sender) || isDrafting(receiver)) {
			event.cancel();
			return;
		}
		UUID senderId = sender.getPlayer().getUuid();
		UUID receiverId = receiver.getPlayer().getUuid();
		if (PelicanManager.shouldCancelVoice(senderId, receiverId)) {
			event.cancel();
		}
	}

	private static boolean isDrafting(VoicechatConnection connection) {
		if (connection == null || connection.getPlayer() == null
				|| connection.getPlayer().getServerLevel() == null) {
			return false;
		}
		Object level = connection.getPlayer().getServerLevel().getServerLevel();
		return level instanceof ServerWorld world && DraftManager.isDrafting(world);
	}

	private void onCreateGroup(CreateGroupEvent event) {
		VoicechatConnection connection = event.getConnection();
		if (connection == null || connection.getPlayer() == null) return;
		Object nativePlayer = connection.getPlayer().getPlayer();
		if (nativePlayer instanceof ServerPlayerEntity player && player.hasPermissionLevel(2)) return;
		event.cancel();
		if (nativePlayer instanceof ServerPlayerEntity player) {
			player.sendMessage(Text.literal("Only operators can create voice chat groups.").formatted(Formatting.RED), true);
		}
	}

	private void onClientReceiveSound(ClientReceiveSoundEvent event) {
		if (event == null || event.getRawAudio() == null || event.getRawAudio().length == 0) return;
		ClientVoiceActivity.markSpeaking(event.getId());
	}

	private void onOpenALSound(OpenALSoundEvent.Post event) {
		float pitch = clientPitchFor(event.getChannelId());
		try {
			org.lwjgl.openal.AL10.alSourcef(event.getSource(), org.lwjgl.openal.AL10.AL_PITCH, pitch);
		} catch (Throwable ignored) {
		}
	}

	private float clientPitchFor(UUID playerId) {
		if (playerId == null) return 1.0F;
		try {
			Method method = clientPitchForMethod();
			if (method == null) return 1.0F;
			Object value = method.invoke(null, playerId);
			return value instanceof Number number ? number.floatValue() : 1.0F;
		} catch (Throwable ignored) {
			return 1.0F;
		}
	}

	private Method clientPitchForMethod() {
		if (lookedUpClientPitchForMethod) return clientPitchForMethod;
		lookedUpClientPitchForMethod = true;
		try {
			Class<?> bridge = Class.forName("dev.mapselect.client.game.ClientVoicePitchBridge");
			clientPitchForMethod = bridge.getMethod("pitchFor", UUID.class);
		} catch (Throwable ignored) {
			clientPitchForMethod = null;
		}
		return clientPitchForMethod;
	}
}
