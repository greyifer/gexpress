package dev.mapselect.network.role.twins;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TwinsSwapPayload() implements CustomPayload {
	public static final Id<TwinsSwapPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "twins_swap"));
	public static final PacketCodec<PacketByteBuf, TwinsSwapPayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new TwinsSwapPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
