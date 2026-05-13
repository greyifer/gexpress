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
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.role.vulture.VultureManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Method;
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
		Set<UUID> bellyReceivers = VultureManager.bellyVoiceReceivers(senderId);
		if (!bellyReceivers.isEmpty()) {
			StaticSoundPacket packet = event.getPacket().staticSoundPacketBuilder()
				.channelId(senderId)
				.opusEncodedData(event.getPacket().getOpusEncodedData())
				.build();
			for (UUID receiverId : bellyReceivers) {
				VoicechatConnection connection = event.getVoicechat().getConnectionOf(receiverId);
				if (connection != null) event.getVoicechat().sendStaticSoundPacketTo(connection, packet);
			}
			if (VultureManager.isStashed(senderId)) {
				event.cancel();
			}
		}
	}

	private void onSoundPacket(SoundPacketEvent<?> event) {
		VoicechatConnection sender = event.getSenderConnection();
		VoicechatConnection receiver = event.getReceiverConnection();
		if (sender == null || receiver == null) return;
		UUID senderId = sender.getPlayer().getUuid();
		UUID receiverId = receiver.getPlayer().getUuid();
		if (VultureManager.shouldCancelVoice(senderId, receiverId)) {
			event.cancel();
		}
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
			Class<?> bridge = Class.forName("dev.mapselect.client.ClientVoicePitchBridge");
			clientPitchForMethod = bridge.getMethod("pitchFor", UUID.class);
		} catch (Throwable ignored) {
			clientPitchForMethod = null;
		}
		return clientPitchForMethod;
	}
}
