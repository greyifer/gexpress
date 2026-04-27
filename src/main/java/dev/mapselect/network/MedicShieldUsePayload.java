package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request to shield the player the Medic is looking at. */
public record MedicShieldUsePayload() implements CustomPayload {
	public static final CustomPayload.Id<MedicShieldUsePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "medic_shield_use"));

	public static final PacketCodec<PacketByteBuf, MedicShieldUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new MedicShieldUsePayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
