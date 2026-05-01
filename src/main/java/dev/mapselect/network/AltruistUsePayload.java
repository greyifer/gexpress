package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AltruistUsePayload() implements CustomPayload {
	public static final Id<AltruistUsePayload> ID = new Id<>(Identifier.of(MapSelect.MOD_ID, "altruist_use"));
	public static final PacketCodec<PacketByteBuf, AltruistUsePayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new AltruistUsePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
