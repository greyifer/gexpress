package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SpyUsePayload() implements CustomPayload {
	public static final Id<SpyUsePayload> ID = new Id<>(Identifier.of(MapSelect.MOD_ID, "spy_use"));
	public static final PacketCodec<PacketByteBuf, SpyUsePayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new SpyUsePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
