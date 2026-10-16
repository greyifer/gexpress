package dev.mapselect.network.role.rat;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RatInvisibilityPayload() implements CustomPayload {
	public static final Id<RatInvisibilityPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "rat_invisibility"));
	public static final PacketCodec<PacketByteBuf, RatInvisibilityPayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new RatInvisibilityPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
