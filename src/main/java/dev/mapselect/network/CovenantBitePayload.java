package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CovenantBitePayload() implements CustomPayload {
	public static final CustomPayload.Id<CovenantBitePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "covenant_bite"));

	public static final PacketCodec<PacketByteBuf, CovenantBitePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new CovenantBitePayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
