package dev.mapselect.network.role.twins;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TwinsVoiceRelayPayload() implements CustomPayload {
	public static final Id<TwinsVoiceRelayPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "twins_voice_relay"));
	public static final PacketCodec<PacketByteBuf, TwinsVoiceRelayPayload> CODEC =
		PacketCodec.unit(new TwinsVoiceRelayPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
