package dev.mapselect.network.role.rat;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RatHuntPayload(List<Hunt> hunts) implements CustomPayload {
	public static final Id<RatHuntPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "rat_hunt"));

	public RatHuntPayload {
		hunts = hunts == null ? List.of() : hunts.stream()
			.filter(hunt -> hunt != null && hunt.ratId() != null)
			.toList();
	}

	public static RatHuntPayload clear() {
		return new RatHuntPayload(List.of());
	}

	public static final PacketCodec<PacketByteBuf, RatHuntPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.hunts().size());
			for (Hunt hunt : payload.hunts()) {
				buf.writeUuid(hunt.ratId());
				buf.writeLong(hunt.endTick());
			}
		},
		buf -> {
			int count = Math.max(0, Math.min(256, buf.readInt()));
			List<Hunt> hunts = new ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				hunts.add(new Hunt(buf.readUuid(), buf.readLong()));
			}
			return new RatHuntPayload(hunts);
		}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record Hunt(UUID ratId, long endTick) {}
}
