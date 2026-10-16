package dev.mapselect.network.role.altruist;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AltruistTerminatePayload() implements CustomPayload {
	public static final Id<AltruistTerminatePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "altruist_terminate"));
	public static final PacketCodec<PacketByteBuf, AltruistTerminatePayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new AltruistTerminatePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
