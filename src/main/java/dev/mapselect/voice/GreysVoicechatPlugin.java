package dev.mapselect.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
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
	private Method clientPitchMethod;
	private boolean lookedUpClientPitchMethod;

	@Override
	public String getPluginId() {
		return "gexpress";
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		try {
			registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
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

	private void onClientSound(ClientSoundEvent event) {
		short[] raw = event.getRawAudio();
		if (raw == null || raw.length == 0) return;
		float pitch = localClientPitch();
		if (Math.abs(pitch - 1.0F) <= 0.03F) return;
		event.setRawAudio(VoicePitchShifter.shift(raw, pitch));
	}

	private float localClientPitch() {
		try {
			Method method = clientPitchMethod();
			if (method == null) return 1.0F;
			Object value = method.invoke(null);
			return value instanceof Number number ? number.floatValue() : 1.0F;
		} catch (Throwable ignored) {
			return 1.0F;
		}
	}

	private Method clientPitchMethod() {
		if (lookedUpClientPitchMethod) return clientPitchMethod;
		lookedUpClientPitchMethod = true;
		try {
			Class<?> bridge = Class.forName("dev.mapselect.client.ClientVoicePitchBridge");
			clientPitchMethod = bridge.getMethod("currentPitch");
		} catch (Throwable ignored) {
			clientPitchMethod = null;
		}
		return clientPitchMethod;
	}
}
