package dev.mapselect.network.role.covenant;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CovenantBatRenderPayload(List<UUID> playerIds) implements CustomPayload {
	public static final CustomPayload.Id<CovenantBatRenderPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "covenant_bat_render"));

	public CovenantBatRenderPayload {
		playerIds = playerIds == null ? List.of() : List.copyOf(playerIds);
	}

	public static final PacketCodec<PacketByteBuf, CovenantBatRenderPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(payload.playerIds().size());
			for (UUID id : payload.playerIds()) buf.writeUuid(id);
		},
		buf -> {
			int size = Math.max(0, Math.min(128, buf.readVarInt()));
			List<UUID> ids = new ArrayList<>(size);
			for (int i = 0; i < size; i++) ids.add(buf.readUuid());
			return new CovenantBatRenderPayload(ids);
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
