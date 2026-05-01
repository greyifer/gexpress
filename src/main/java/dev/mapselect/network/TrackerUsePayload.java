package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TrackerUsePayload() implements CustomPayload {
	public static final Id<TrackerUsePayload> ID = new Id<>(Identifier.of(MapSelect.MOD_ID, "tracker_use"));
	public static final PacketCodec<PacketByteBuf, TrackerUsePayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new TrackerUsePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
