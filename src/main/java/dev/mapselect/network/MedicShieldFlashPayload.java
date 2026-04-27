package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record MedicShieldFlashPayload(UUID targetUuid, boolean broken) implements CustomPayload {
	public static final CustomPayload.Id<MedicShieldFlashPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "medic_shield_flash"));

	public static final PacketCodec<PacketByteBuf, MedicShieldFlashPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeUuid(payload.targetUuid());
			buf.writeBoolean(payload.broken());
		},
		buf -> new MedicShieldFlashPayload(buf.readUuid(), buf.readBoolean())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
