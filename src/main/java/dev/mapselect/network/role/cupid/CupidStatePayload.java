package dev.mapselect.network.role.cupid;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CupidStatePayload(boolean selecting, UUID firstTargetId, String firstTargetName,
		List<Pair> pairs, int linkedAlivePlayers, int requiredLinkedPlayers) implements CustomPayload {
	private static final int MAX_PAIRS = 16;
	public static final CustomPayload.Id<CupidStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "cupid_state"));

	public CupidStatePayload {
		if (!selecting) firstTargetId = null;
		firstTargetName = firstTargetName == null ? "" : firstTargetName;
		pairs = pairs == null ? List.of() : List.copyOf(pairs.stream()
			.filter(Objects::nonNull)
			.limit(MAX_PAIRS)
			.toList());
		linkedAlivePlayers = Math.max(0, linkedAlivePlayers) & ~1;
		requiredLinkedPlayers = Math.max(0, requiredLinkedPlayers) & ~1;
	}

	public static CupidStatePayload clear() {
		return new CupidStatePayload(false, null, "", List.of(), 0, 0);
	}

	public static final PacketCodec<PacketByteBuf, CupidStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.selecting());
			buf.writeBoolean(payload.firstTargetId() != null);
			if (payload.firstTargetId() != null) buf.writeUuid(payload.firstTargetId());
			buf.writeString(payload.firstTargetName(), 64);
			buf.writeVarInt(payload.pairs().size());
			for (Pair pair : payload.pairs()) {
				buf.writeUuid(pair.firstId());
				buf.writeString(pair.firstName(), 64);
				buf.writeUuid(pair.secondId());
				buf.writeString(pair.secondName(), 64);
			}
			buf.writeVarInt(payload.linkedAlivePlayers());
			buf.writeVarInt(payload.requiredLinkedPlayers());
		},
		buf -> {
			boolean selecting = buf.readBoolean();
			UUID firstTargetId = buf.readBoolean() ? buf.readUuid() : null;
			String firstTargetName = buf.readString(64);
			int size = Math.max(0, Math.min(MAX_PAIRS, buf.readVarInt()));
			java.util.ArrayList<Pair> pairs = new java.util.ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				pairs.add(new Pair(buf.readUuid(), buf.readString(64), buf.readUuid(), buf.readString(64)));
			}
			return new CupidStatePayload(selecting, firstTargetId, firstTargetName, pairs,
				buf.readVarInt(), buf.readVarInt());
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record Pair(UUID firstId, String firstName, UUID secondId, String secondName) {
		public Pair {
			firstName = firstName == null ? "" : firstName;
			secondName = secondName == null ? "" : secondName;
		}
	}
}
