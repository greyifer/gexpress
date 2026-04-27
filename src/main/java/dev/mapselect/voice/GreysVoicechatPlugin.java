package dev.mapselect.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import dev.mapselect.role.trickster.TricksterManager;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.voice.VoiceMuteState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public class GreysVoicechatPlugin implements VoicechatPlugin {
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
		if (TricksterManager.isGlobalMuteActive(world)) {
			event.cancel();
			return;
		}
		VoiceMuteState state = VoiceMuteState.KEY.getNullable(world);
		if (state != null && state.isMuted(senderId)) {
			event.cancel();
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
}
