package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Server -> client state for a player currently frozen by a Time Master. */
public record TimeMasterFreezeStatePayload(boolean frozen, UUID targetId, UUID timeMasterId, int durationTicks)
		implements CustomPayload {
	private static final UUID ZERO_UUID = new UUID(0L, 0L);

	public static final CustomPayload.Id<TimeMasterFreezeStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "time_master_freeze_state"));

	public static TimeMasterFreezeStatePayload clear(UUID targetId) {
		return new TimeMasterFreezeStatePayload(false, targetId, ZERO_UUID, 0);
	}

	public static final PacketCodec<PacketByteBuf, TimeMasterFreezeStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.frozen());
			buf.writeUuid(payload.targetId());
			buf.writeUuid(payload.timeMasterId() == null ? ZERO_UUID : payload.timeMasterId());
			buf.writeInt(payload.durationTicks());
		},
		buf -> new TimeMasterFreezeStatePayload(buf.readBoolean(), buf.readUuid(), buf.readUuid(), buf.readInt())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
