package dev.mapselect.network.poll;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LobbyPollCreatePayload(String targetType, String targetId, String setting, int value)
		implements CustomPayload {
	public static final Id<LobbyPollCreatePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "lobby_poll_create"));

	public LobbyPollCreatePayload {
		targetType = targetType == null ? "" : targetType;
		targetId = targetId == null ? "" : targetId;
		setting = setting == null ? "" : setting;
	}

	public static final PacketCodec<PacketByteBuf, LobbyPollCreatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeString(payload.targetType(), 24);
			buf.writeString(payload.targetId(), 128);
			buf.writeString(payload.setting(), 24);
			buf.writeInt(payload.value());
		},
		buf -> new LobbyPollCreatePayload(
			buf.readString(24),
			buf.readString(128),
			buf.readString(24),
			buf.readInt()
		)
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
