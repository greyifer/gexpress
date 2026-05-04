package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record MafiaStatePayload(List<UUID> memberIds) implements CustomPayload {
	public static final Id<MafiaStatePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "mafia_state"));

	public MafiaStatePayload {
		memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
	}

	public static final PacketCodec<PacketByteBuf, MafiaStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.memberIds().size());
			for (UUID id : payload.memberIds()) buf.writeUuid(id);
		},
		buf -> {
			int size = Math.max(0, buf.readInt());
			List<UUID> ids = new ArrayList<>(size);
			for (int i = 0; i < size; i++) ids.add(buf.readUuid());
			return new MafiaStatePayload(ids);
		}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
