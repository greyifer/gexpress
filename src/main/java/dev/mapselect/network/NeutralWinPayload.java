package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record NeutralWinPayload(UUID winnerId, String translationKey, int color) implements CustomPayload {
	public static final CustomPayload.Id<NeutralWinPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "neutral_win"));

	public static final PacketCodec<PacketByteBuf, NeutralWinPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeUuid(payload.winnerId());
			buf.writeString(payload.translationKey());
			buf.writeInt(payload.color());
		},
		buf -> new NeutralWinPayload(buf.readUuid(), buf.readString(128), buf.readInt())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
