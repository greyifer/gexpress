package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record BodyguardStatePayload(boolean active, UUID targetId, String targetName) implements CustomPayload {
	public static final Id<BodyguardStatePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "bodyguard_state"));

	public BodyguardStatePayload {
		targetName = targetName == null ? "" : targetName;
	}

	public static final PacketCodec<PacketByteBuf, BodyguardStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeBoolean(payload.targetId() != null);
			if (payload.targetId() != null) buf.writeUuid(payload.targetId());
			buf.writeString(payload.targetName(), 64);
		},
		buf -> {
			boolean active = buf.readBoolean();
			UUID targetId = buf.readBoolean() ? buf.readUuid() : null;
			return new BodyguardStatePayload(active, targetId, buf.readString(64));
		}
	);

	public static BodyguardStatePayload clear() {
		return new BodyguardStatePayload(false, null, "");
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
