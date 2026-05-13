package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server -> client Pelican win-progress counter. */
public record VultureProgressPayload(boolean show, int eaten, int required, List<BellyEntry> belly)
		implements CustomPayload {
	public static final CustomPayload.Id<VultureProgressPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "vulture_progress"));

	public VultureProgressPayload {
		belly = belly == null ? List.of() : List.copyOf(belly);
	}

	public static final PacketCodec<PacketByteBuf, VultureProgressPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.show());
			buf.writeInt(payload.eaten());
			buf.writeInt(payload.required());
			buf.writeInt(payload.belly().size());
			for (BellyEntry entry : payload.belly()) {
				buf.writeUuid(entry.playerId());
				buf.writeString(entry.name(), 64);
			}
		},
		buf -> {
			boolean show = buf.readBoolean();
			int eaten = buf.readInt();
			int required = buf.readInt();
			int size = Math.max(0, Math.min(32, buf.readInt()));
			List<BellyEntry> belly = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				belly.add(new BellyEntry(buf.readUuid(), buf.readString(64)));
			}
			return new VultureProgressPayload(show, eaten, required, belly);
		}
	);

	public static VultureProgressPayload clear() {
		return new VultureProgressPayload(false, 0, 1, List.of());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record BellyEntry(UUID playerId, String name) {
		public BellyEntry {
			name = name == null ? "" : name;
		}
	}
}
