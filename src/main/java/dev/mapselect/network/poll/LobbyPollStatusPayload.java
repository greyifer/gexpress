package dev.mapselect.network.poll;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LobbyPollStatusPayload(boolean active, String title, String details, int yesVotes, int noVotes,
		int playerCount, int remainingTicks) implements CustomPayload {
	public static final Id<LobbyPollStatusPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "lobby_poll_status"));

	public LobbyPollStatusPayload {
		title = title == null ? "" : title;
		details = details == null ? "" : details;
		yesVotes = Math.max(0, yesVotes);
		noVotes = Math.max(0, noVotes);
		playerCount = Math.max(0, playerCount);
		remainingTicks = Math.max(0, remainingTicks);
	}

	public static final PacketCodec<PacketByteBuf, LobbyPollStatusPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeString(payload.title(), 128);
			buf.writeString(payload.details(), 256);
			buf.writeInt(payload.yesVotes());
			buf.writeInt(payload.noVotes());
			buf.writeInt(payload.playerCount());
			buf.writeInt(payload.remainingTicks());
		},
		buf -> new LobbyPollStatusPayload(
			buf.readBoolean(),
			buf.readString(128),
			buf.readString(256),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt()
		)
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
