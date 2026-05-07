package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request for a dead guardian angel to shield the looked-at player. */
public record GuardianAngelShieldUsePayload() implements CustomPayload {
	public static final CustomPayload.Id<GuardianAngelShieldUsePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "guardian_angel_shield_use"));

	public static final PacketCodec<PacketByteBuf, GuardianAngelShieldUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new GuardianAngelShieldUsePayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
