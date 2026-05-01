package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TrackerStatePayload(List<UUID> trackedIds) implements CustomPayload {
	public static final Id<TrackerStatePayload> ID = new Id<>(Identifier.of(MapSelect.MOD_ID, "tracker_state"));

	public TrackerStatePayload {
		trackedIds = trackedIds == null ? List.of() : List.copyOf(trackedIds);
	}

	public static final PacketCodec<PacketByteBuf, TrackerStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.trackedIds().size());
			for (UUID id : payload.trackedIds()) buf.writeUuid(id);
		},
		buf -> {
			int size = Math.max(0, buf.readInt());
			List<UUID> ids = new ArrayList<>(size);
			for (int i = 0; i < size; i++) ids.add(buf.readUuid());
			return new TrackerStatePayload(ids);
		}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
