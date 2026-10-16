package dev.mapselect.network.role.rat;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RatVisibilityPayload(List<UUID> invisibleRatIds) implements CustomPayload {
	public static final Id<RatVisibilityPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "rat_visibility"));

	public RatVisibilityPayload {
		invisibleRatIds = invisibleRatIds == null ? List.of() : List.copyOf(invisibleRatIds);
	}

	public static final PacketCodec<PacketByteBuf, RatVisibilityPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.invisibleRatIds().size());
			for (UUID id : payload.invisibleRatIds()) buf.writeUuid(id);
		},
		buf -> {
			int count = Math.max(0, Math.min(256, buf.readInt()));
			List<UUID> ids = new ArrayList<>(count);
			for (int index = 0; index < count; index++) ids.add(buf.readUuid());
			return new RatVisibilityPayload(ids);
		}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
