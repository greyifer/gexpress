package dev.mapselect.network.role.covenant;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CovenantTurnPayload() implements CustomPayload {
	public static final CustomPayload.Id<CovenantTurnPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "covenant_turn"));

	public static final PacketCodec<PacketByteBuf, CovenantTurnPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new CovenantTurnPayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
