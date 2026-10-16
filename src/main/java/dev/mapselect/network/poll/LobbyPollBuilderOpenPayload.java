package dev.mapselect.network.poll;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LobbyPollBuilderOpenPayload() implements CustomPayload {
	public static final Id<LobbyPollBuilderOpenPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "lobby_poll_builder_open"));

	public static final PacketCodec<PacketByteBuf, LobbyPollBuilderOpenPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new LobbyPollBuilderOpenPayload()
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
