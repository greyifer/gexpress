package dev.mapselect.network.poll;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LobbyPollVotePayload(boolean approve) implements CustomPayload {
	public static final Id<LobbyPollVotePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "lobby_poll_vote"));

	public static final PacketCodec<PacketByteBuf, LobbyPollVotePayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeBoolean(payload.approve()),
		buf -> new LobbyPollVotePayload(buf.readBoolean())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
