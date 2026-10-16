package dev.mapselect.network.game;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AutoGameStatusPayload(boolean active, int countdownSeconds, int readyPlayers, int minimumPlayers)
	implements CustomPayload {
	public static final Id<AutoGameStatusPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "auto_game_status"));

	public AutoGameStatusPayload {
		countdownSeconds = Math.max(-1, countdownSeconds);
		readyPlayers = Math.max(0, readyPlayers);
		minimumPlayers = Math.max(1, minimumPlayers);
	}

	public static final PacketCodec<PacketByteBuf, AutoGameStatusPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeInt(payload.countdownSeconds());
			buf.writeInt(payload.readyPlayers());
			buf.writeInt(payload.minimumPlayers());
		},
		buf -> new AutoGameStatusPayload(
			buf.readBoolean(),
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
