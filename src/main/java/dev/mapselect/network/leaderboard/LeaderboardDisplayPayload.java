package dev.mapselect.network.leaderboard;

import dev.mapselect.MapSelect;
import dev.mapselect.leaderboard.LeaderboardCategory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record LeaderboardDisplayPayload(BlockPos origin, LeaderboardCategory category,
		int scrollOffset) implements CustomPayload {
	public static final Id<LeaderboardDisplayPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "leaderboard_display"));

	public LeaderboardDisplayPayload {
		origin = origin == null ? BlockPos.ORIGIN : origin.toImmutable();
		category = category == null ? LeaderboardCategory.KILLS : category;
		scrollOffset = Math.max(0, scrollOffset);
	}

	public static final PacketCodec<PacketByteBuf, LeaderboardDisplayPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBlockPos(payload.origin());
			buf.writeVarInt(payload.category().ordinal());
			buf.writeVarInt(payload.scrollOffset());
		},
		buf -> new LeaderboardDisplayPayload(buf.readBlockPos(),
			LeaderboardCategory.byOrdinal(buf.readVarInt()), buf.readVarInt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
