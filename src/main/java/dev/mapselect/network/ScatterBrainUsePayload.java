package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ScatterBrainUsePayload() implements CustomPayload {
	public static final Id<ScatterBrainUsePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "scatter_brain_use"));
	public static final PacketCodec<PacketByteBuf, ScatterBrainUsePayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new ScatterBrainUsePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
