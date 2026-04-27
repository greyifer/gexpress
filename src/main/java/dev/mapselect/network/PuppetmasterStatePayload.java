package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Server -> client control-session state for camera and victim overlay. */
public record PuppetmasterStatePayload(boolean active, UUID controllerId, UUID targetId, int targetEntityId)
		implements CustomPayload {
	public static final CustomPayload.Id<PuppetmasterStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "puppetmaster_state"));

	public static PuppetmasterStatePayload clear() {
		return new PuppetmasterStatePayload(false, null, null, -1);
	}

	public static final PacketCodec<PacketByteBuf, PuppetmasterStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeBoolean(payload.controllerId() != null);
			if (payload.controllerId() != null) buf.writeUuid(payload.controllerId());
			buf.writeBoolean(payload.targetId() != null);
			if (payload.targetId() != null) buf.writeUuid(payload.targetId());
			buf.writeInt(payload.targetEntityId());
		},
		buf -> {
			boolean active = buf.readBoolean();
			UUID controller = buf.readBoolean() ? buf.readUuid() : null;
			UUID target = buf.readBoolean() ? buf.readUuid() : null;
			int entityId = buf.readInt();
			return new PuppetmasterStatePayload(active, controller, target, entityId);
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
