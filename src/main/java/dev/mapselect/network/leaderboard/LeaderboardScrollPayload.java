package dev.mapselect.network.leaderboard;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record LeaderboardScrollPayload(BlockPos pos, int amount) implements CustomPayload {
	public static final Id<LeaderboardScrollPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "leaderboard_scroll"));

	public LeaderboardScrollPayload {
		pos = pos == null ? BlockPos.ORIGIN : pos.toImmutable();
		amount = Math.max(-3, Math.min(3, amount));
	}

	public static final PacketCodec<PacketByteBuf, LeaderboardScrollPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBlockPos(payload.pos());
			buf.writeByte(payload.amount());
		},
		buf -> new LeaderboardScrollPayload(buf.readBlockPos(), buf.readByte())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
