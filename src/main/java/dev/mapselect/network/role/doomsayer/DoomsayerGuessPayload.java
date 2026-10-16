package dev.mapselect.network.role.doomsayer;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record DoomsayerGuessPayload(UUID targetId, String rolePath) implements CustomPayload {
	public static final Id<DoomsayerGuessPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "doomsayer_guess"));

	public DoomsayerGuessPayload {
		rolePath = rolePath == null ? "" : rolePath;
	}

	public static final PacketCodec<PacketByteBuf, DoomsayerGuessPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeUuid(payload.targetId());
			buf.writeString(payload.rolePath(), 96);
		},
		buf -> new DoomsayerGuessPayload(buf.readUuid(), buf.readString(96))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
