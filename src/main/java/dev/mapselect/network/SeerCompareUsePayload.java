package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SeerCompareUsePayload() implements CustomPayload {
	public static final CustomPayload.Id<SeerCompareUsePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "seer_compare_use"));

	public static final PacketCodec<PacketByteBuf, SeerCompareUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new SeerCompareUsePayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
