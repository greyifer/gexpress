package dev.mapselect.network.role.veteran;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VeteranUsePayload() implements CustomPayload {
	public static final Id<VeteranUsePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "veteran_use"));
	public static final PacketCodec<PacketByteBuf, VeteranUsePayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new VeteranUsePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
