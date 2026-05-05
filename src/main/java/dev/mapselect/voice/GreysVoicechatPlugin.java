package dev.mapselect.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.modifier.ModifierUtils;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.role.trickster.TricksterManager;
import dev.mapselect.role.vulture.VultureManager;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GreysVoicechatPlugin implements VoicechatPlugin {
	private final Map<UUID, CodecPair> codecs = new ConcurrentHashMap<>();

	@Override
	public String getPluginId() {
		return "gexpress";
	}

	@Override
	public void registerEvents(EventRegistration registration) {
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
		if (nativePlayer instanceof ServerPlayerEntity player && GameFunctions.isPlayerAliveAndSurvival(player)) {
			float pitch = TricksterManager.voicePitchFor(world, senderId);
			if (ModifierUtils.has(player, MapSelectModifiers.SQUEAKER_ID)) {
				pitch *= GexpressConfig.getSqueakerPitchPercent() / 100.0F;
			}
			if (Math.abs(pitch - 1.0F) > 0.03F) {
				pitchPacket(event, senderId, pitch);
			}
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

	private void pitchPacket(MicrophonePacketEvent event, UUID senderId, float pitch) {
		try {
			VoicechatServerApi api = event.getVoicechat();
			CodecPair codec = codecs.computeIfAbsent(senderId,
				id -> new CodecPair(api.createDecoder(), api.createEncoder()));
			short[] decoded = codec.decoder().decode(event.getPacket().getOpusEncodedData());
			if (decoded == null || decoded.length == 0) return;
			short[] shifted = shiftPitch(decoded, Math.max(0.5F, Math.min(2.0F, pitch)));
			event.getPacket().setOpusEncodedData(codec.encoder().encode(shifted));
		} catch (Throwable ignored) {
			codecs.remove(senderId);
		}
	}

	private static short[] shiftPitch(short[] input, float pitch) {
		short[] out = new short[input.length];
		int max = input.length - 1;
		if (max <= 0) return input.clone();
		for (int i = 0; i < out.length; i++) {
			double source = i * pitch;
			while (source > max) source -= max;
			int a = Math.max(0, Math.min(max, (int) source));
			int b = Math.min(max, a + 1);
			double t = source - a;
			out[i] = (short) Math.round(input[a] * (1.0D - t) + input[b] * t);
		}
		return out;
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

	private record CodecPair(OpusDecoder decoder, OpusEncoder encoder) {}
}
