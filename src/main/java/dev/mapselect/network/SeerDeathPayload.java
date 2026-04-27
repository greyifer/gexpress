package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client signal that a Seer should flash when someone dies. */
public record SeerDeathPayload() implements CustomPayload {
	public static final CustomPayload.Id<SeerDeathPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "seer_death"));

	public static final PacketCodec<PacketByteBuf, SeerDeathPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new SeerDeathPayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
