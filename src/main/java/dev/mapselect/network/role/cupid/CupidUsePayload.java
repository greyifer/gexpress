package dev.mapselect.network.role.cupid;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record CupidUsePayload(UUID targetId) implements CustomPayload {
	public static final CustomPayload.Id<CupidUsePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "cupid_use"));

	public static final PacketCodec<PacketByteBuf, CupidUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.targetId() != null);
			if (payload.targetId() != null) buf.writeUuid(payload.targetId());
		},
		buf -> new CupidUsePayload(buf.readBoolean() ? buf.readUuid() : null)
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
