package dev.mapselect.network.role.pelican;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Client -> server request to digest one player currently inside the Pelican. */
public record VultureDigestPayload(UUID targetId) implements CustomPayload {
	public static final Id<VultureDigestPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "vulture_digest"));

	public static final PacketCodec<PacketByteBuf, VultureDigestPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeUuid(payload.targetId()),
		buf -> new VultureDigestPayload(buf.readUuid())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
