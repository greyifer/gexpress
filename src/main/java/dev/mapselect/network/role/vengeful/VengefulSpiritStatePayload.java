package dev.mapselect.network.role.vengeful;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record VengefulSpiritStatePayload(int phase, UUID killerId, String killerName,
		long remainingTicks, long totalTicks) implements CustomPayload {
	public static final int NONE = 0;
	public static final int PENDING = 1;
	public static final int ACTIVE = 2;
	public static final CustomPayload.Id<VengefulSpiritStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "vengeful_spirit_state"));

	public VengefulSpiritStatePayload {
		phase = Math.max(NONE, Math.min(ACTIVE, phase));
		killerName = killerName == null ? "" : killerName;
		remainingTicks = Math.max(0L, remainingTicks);
		totalTicks = Math.max(0L, totalTicks);
	}

	public static VengefulSpiritStatePayload clear() {
		return new VengefulSpiritStatePayload(NONE, null, "", 0L, 0L);
	}

	public static final PacketCodec<PacketByteBuf, VengefulSpiritStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(payload.phase());
			buf.writeBoolean(payload.killerId() != null);
			if (payload.killerId() != null) buf.writeUuid(payload.killerId());
			buf.writeString(payload.killerName(), 64);
			buf.writeVarLong(payload.remainingTicks());
			buf.writeVarLong(payload.totalTicks());
		},
		buf -> new VengefulSpiritStatePayload(buf.readVarInt(), buf.readBoolean() ? buf.readUuid() : null,
			buf.readString(64), buf.readVarLong(), buf.readVarLong())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
