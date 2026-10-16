package dev.mapselect.network.role.painter;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PainterStatePayload(List<Entry> bodyTints, List<Entry> playerTints) implements CustomPayload {
	public static final Id<PainterStatePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "painter_state"));

	public PainterStatePayload {
		bodyTints = normalize(bodyTints);
		playerTints = normalize(playerTints);
	}

	public static PainterStatePayload clear() {
		return new PainterStatePayload(List.of(), List.of());
	}

	public static final PacketCodec<PacketByteBuf, PainterStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			writeEntries(buf, payload.bodyTints());
			writeEntries(buf, payload.playerTints());
		},
		buf -> new PainterStatePayload(readEntries(buf), readEntries(buf))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void writeEntries(PacketByteBuf buf, List<Entry> entries) {
		buf.writeVarInt(entries.size());
		for (Entry entry : entries) {
			buf.writeUuid(entry.targetId());
			buf.writeInt(entry.color());
			buf.writeVarInt(entry.remainingTicks());
			buf.writeVarInt(entry.totalTicks());
		}
	}

	private static List<Entry> readEntries(PacketByteBuf buf) {
		int size = Math.max(0, Math.min(256, buf.readVarInt()));
		List<Entry> entries = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			entries.add(new Entry(buf.readUuid(), buf.readInt(), buf.readVarInt(), buf.readVarInt()));
		}
		return entries;
	}

	private static List<Entry> normalize(List<Entry> entries) {
		if (entries == null || entries.isEmpty()) return List.of();
		return List.copyOf(entries.stream()
			.filter(entry -> entry != null && entry.targetId() != null && entry.totalTicks() > 0)
			.limit(256)
			.toList());
	}

	public record Entry(UUID targetId, int color, int remainingTicks, int totalTicks) {
		public Entry {
			color &= 0xFFFFFF;
			remainingTicks = Math.max(0, remainingTicks);
			totalTicks = Math.max(1, totalTicks);
		}
	}
}
