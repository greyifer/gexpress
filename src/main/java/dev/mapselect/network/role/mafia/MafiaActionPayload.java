package dev.mapselect.network.role.mafia;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record MafiaActionPayload(int action, UUID targetId) implements CustomPayload {
	public static final int RECRUIT_MAFIOSO = 0;
	public static final int RECRUIT_JANITOR = 1;
	public static final int CLEAN_BODY = 2;
	public static final int RECRUIT_PICKPOCKET = 3;
	public static final int PICKPOCKET_START = 4;
	public static final int PICKPOCKET_STOP = 5;
	public static final int RECRUIT_BURGLAR = 6;
	public static final int OPEN_RECRUIT_MENU = 7;

	public static final Id<MafiaActionPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "mafia_action"));

	public static final PacketCodec<PacketByteBuf, MafiaActionPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.action());
			buf.writeBoolean(payload.targetId() != null);
			if (payload.targetId() != null) buf.writeUuid(payload.targetId());
		},
		buf -> new MafiaActionPayload(buf.readInt(), buf.readBoolean() ? buf.readUuid() : null)
	);

	public MafiaActionPayload(int action) {
		this(action, null);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
