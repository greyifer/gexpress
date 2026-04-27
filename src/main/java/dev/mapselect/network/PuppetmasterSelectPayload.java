package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Client -> server target selection from the Puppetmaster menu. */
public record PuppetmasterSelectPayload(UUID targetId) implements CustomPayload {
	public static final CustomPayload.Id<PuppetmasterSelectPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "puppetmaster_select"));

	public static final PacketCodec<PacketByteBuf, PuppetmasterSelectPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeUuid(payload.targetId()),
		buf -> new PuppetmasterSelectPayload(buf.readUuid())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
