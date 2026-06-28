package dev.mapselect.network.role.painter;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record PainterPlayerUsePayload(UUID targetId) implements CustomPayload {
	public static final Id<PainterPlayerUsePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "painter_player_use"));

	public static final PacketCodec<PacketByteBuf, PainterPlayerUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.targetId() != null);
			if (payload.targetId() != null) buf.writeUuid(payload.targetId());
		},
		buf -> new PainterPlayerUsePayload(buf.readBoolean() ? buf.readUuid() : null)
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
