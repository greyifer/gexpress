package dev.mapselect.network.role.sniffer;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SnifferUsePayload() implements CustomPayload {
	public static final Id<SnifferUsePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "sniffer_use"));
	public static final PacketCodec<PacketByteBuf, SnifferUsePayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new SnifferUsePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
