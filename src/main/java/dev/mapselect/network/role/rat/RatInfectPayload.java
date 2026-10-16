package dev.mapselect.network.role.rat;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RatInfectPayload() implements CustomPayload {
	public static final Id<RatInfectPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "rat_infect"));
	public static final PacketCodec<PacketByteBuf, RatInfectPayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new RatInfectPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
