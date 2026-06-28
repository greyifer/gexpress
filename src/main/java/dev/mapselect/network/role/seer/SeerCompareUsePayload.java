package dev.mapselect.network.role.seer;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record SeerCompareUsePayload(UUID targetId) implements CustomPayload {
	public static final CustomPayload.Id<SeerCompareUsePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "seer_compare_use"));

	public static final PacketCodec<PacketByteBuf, SeerCompareUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.targetId() != null);
			if (payload.targetId() != null) buf.writeUuid(payload.targetId());
		},
		buf -> new SeerCompareUsePayload(buf.readBoolean() ? buf.readUuid() : null)
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
