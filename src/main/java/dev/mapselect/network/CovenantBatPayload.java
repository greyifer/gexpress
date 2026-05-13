package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CovenantBatPayload() implements CustomPayload {
	public static final CustomPayload.Id<CovenantBatPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "covenant_bat"));

	public static final PacketCodec<PacketByteBuf, CovenantBatPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new CovenantBatPayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
