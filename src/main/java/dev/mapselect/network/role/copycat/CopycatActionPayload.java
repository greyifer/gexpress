package dev.mapselect.network.role.copycat;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record CopycatActionPayload(Action action, int index, UUID targetId) implements CustomPayload {
	public static final CustomPayload.Id<CopycatActionPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "copycat_action"));

	public CopycatActionPayload {
		action = action == null ? Action.STORE : action;
		index = Math.max(0, Math.min(2, index));
		if (action != Action.STORE_TARGET) targetId = null;
	}

	public static CopycatActionPayload store() {
		return new CopycatActionPayload(Action.STORE, 0, null);
	}

	public static CopycatActionPayload storeTarget(UUID targetId) {
		return new CopycatActionPayload(Action.STORE_TARGET, 0, targetId);
	}

	public static CopycatActionPayload selectStored(int index) {
		return new CopycatActionPayload(Action.SELECT_STORED, index, null);
	}

	public static CopycatActionPayload activateStored(int index) {
		return new CopycatActionPayload(Action.ACTIVATE_STORED, index, null);
	}

	public static CopycatActionPayload cancelActive() {
		return new CopycatActionPayload(Action.CANCEL_ACTIVE, 0, null);
	}

	public static final PacketCodec<PacketByteBuf, CopycatActionPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeEnumConstant(payload.action());
			buf.writeVarInt(payload.index());
			buf.writeBoolean(payload.targetId() != null);
			if (payload.targetId() != null) buf.writeUuid(payload.targetId());
		},
		buf -> new CopycatActionPayload(buf.readEnumConstant(Action.class), buf.readVarInt(),
			buf.readBoolean() ? buf.readUuid() : null)
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	public enum Action {
		STORE,
		STORE_TARGET,
		SELECT_STORED,
		ACTIVATE_STORED,
		CANCEL_ACTIVE
	}
}
