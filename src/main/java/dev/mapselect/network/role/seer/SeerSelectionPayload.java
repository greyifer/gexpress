package dev.mapselect.network.role.seer;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record SeerSelectionPayload(boolean active, UUID targetId, String targetName) implements CustomPayload {
	public static final CustomPayload.Id<SeerSelectionPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "seer_selection"));

	public SeerSelectionPayload {
		if (!active) targetId = null;
		targetName = targetName == null ? "" : targetName;
	}

	public static SeerSelectionPayload clear() {
		return new SeerSelectionPayload(false, null, "");
	}

	public static final PacketCodec<PacketByteBuf, SeerSelectionPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeBoolean(payload.targetId() != null);
			if (payload.targetId() != null) buf.writeUuid(payload.targetId());
			buf.writeString(payload.targetName(), 64);
		},
		buf -> {
			boolean active = buf.readBoolean();
			UUID targetId = buf.readBoolean() ? buf.readUuid() : null;
			return new SeerSelectionPayload(active, targetId, buf.readString(64));
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
