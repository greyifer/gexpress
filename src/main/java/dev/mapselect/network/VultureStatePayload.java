package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Server -> client state for players currently stashed inside a Vulture. */
public record VultureStatePayload(boolean stashed, UUID vultureId, int vultureEntityId)
		implements CustomPayload {
	public static final CustomPayload.Id<VultureStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "vulture_state"));

	public static VultureStatePayload clear() {
		return new VultureStatePayload(false, null, -1);
	}

	public static final PacketCodec<PacketByteBuf, VultureStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.stashed());
			buf.writeBoolean(payload.vultureId() != null);
			if (payload.vultureId() != null) buf.writeUuid(payload.vultureId());
			buf.writeInt(payload.vultureEntityId());
		},
		buf -> {
			boolean stashed = buf.readBoolean();
			UUID vulture = buf.readBoolean() ? buf.readUuid() : null;
			int entityId = buf.readInt();
			return new VultureStatePayload(stashed, vulture, entityId);
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
