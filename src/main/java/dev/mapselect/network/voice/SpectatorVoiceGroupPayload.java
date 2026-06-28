package dev.mapselect.network.voice;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SpectatorVoiceGroupPayload(int delta) implements CustomPayload {
	public static final CustomPayload.Id<SpectatorVoiceGroupPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "spectator_voice_group"));

	public SpectatorVoiceGroupPayload {
		delta = delta < 0 ? -1 : delta > 0 ? 1 : 0;
	}

	public static final PacketCodec<PacketByteBuf, SpectatorVoiceGroupPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeByte(payload.delta()),
		buf -> new SpectatorVoiceGroupPayload(buf.readByte())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
