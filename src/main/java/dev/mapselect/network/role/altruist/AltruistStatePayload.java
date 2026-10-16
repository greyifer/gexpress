package dev.mapselect.network.role.altruist;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record AltruistStatePayload(List<Entry> revivedPlayers, int revivesUsed, int maxRevives)
		implements CustomPayload {
	public static final Id<AltruistStatePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "altruist_state"));

	public AltruistStatePayload {
		revivedPlayers = revivedPlayers == null ? List.of() : List.copyOf(revivedPlayers);
		revivesUsed = Math.max(0, revivesUsed);
		maxRevives = Math.max(1, maxRevives);
	}

	public static final PacketCodec<PacketByteBuf, AltruistStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.revivedPlayers().size());
			for (Entry entry : payload.revivedPlayers()) {
				buf.writeUuid(entry.playerId());
				buf.writeString(entry.name(), 64);
			}
			buf.writeInt(payload.revivesUsed());
			buf.writeInt(payload.maxRevives());
		},
		buf -> {
			int encodedSize = Math.max(0, Math.min(64, buf.readInt()));
			List<Entry> entries = new ArrayList<>(Math.min(2, encodedSize));
			for (int i = 0; i < encodedSize; i++) {
				Entry entry = new Entry(buf.readUuid(), buf.readString(64));
				if (i < 2) entries.add(entry);
			}
			return new AltruistStatePayload(entries, buf.readInt(), buf.readInt());
		}
	);

	public static AltruistStatePayload clear() {
		return new AltruistStatePayload(List.of(), 0, 2);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record Entry(UUID playerId, String name) {
		public Entry {
			name = name == null ? "" : name;
		}
	}
}
