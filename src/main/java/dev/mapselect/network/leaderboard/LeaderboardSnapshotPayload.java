package dev.mapselect.network.leaderboard;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record LeaderboardSnapshotPayload(List<Entry> entries) implements CustomPayload {
	public static final Id<LeaderboardSnapshotPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "leaderboard_snapshot"));
	private static final int MAX_ENTRIES = 4096;
	private static final int MAX_TEXTURE_VALUE_LENGTH = 16_384;
	private static final int MAX_TEXTURE_SIGNATURE_LENGTH = 2_048;

	public LeaderboardSnapshotPayload {
		entries = entries == null ? List.of() : List.copyOf(entries.subList(0,
			Math.min(MAX_ENTRIES, entries.size())));
	}

	public static final PacketCodec<PacketByteBuf, LeaderboardSnapshotPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(payload.entries().size());
			for (Entry entry : payload.entries()) {
				buf.writeUuid(entry.playerId());
				buf.writeString(entry.name(), 64);
				buf.writeString(entry.textureValue(), MAX_TEXTURE_VALUE_LENGTH);
				buf.writeString(entry.textureSignature(), MAX_TEXTURE_SIGNATURE_LENGTH);
				buf.writeVarInt(entry.kills());
				buf.writeVarInt(entry.level());
				buf.writeVarInt(entry.wins());
				buf.writeVarInt(entry.killerWins());
				buf.writeVarInt(entry.neutralWins());
				buf.writeVarInt(entry.civilianWins());
				buf.writeVarInt(entry.longestWinStreak());
				buf.writeVarLong(entry.longestAliveTicks());
			}
		},
		buf -> {
			int size = Math.max(0, Math.min(MAX_ENTRIES, buf.readVarInt()));
			List<Entry> entries = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				entries.add(new Entry(buf.readUuid(), buf.readString(64),
					buf.readString(MAX_TEXTURE_VALUE_LENGTH),
					buf.readString(MAX_TEXTURE_SIGNATURE_LENGTH),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarLong()));
			}
			return new LeaderboardSnapshotPayload(entries);
		}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record Entry(UUID playerId, String name, String textureValue, String textureSignature,
			int kills, int level, int wins,
			int killerWins, int neutralWins, int civilianWins, int longestWinStreak,
			long longestAliveTicks) {
		public Entry {
			name = name == null || name.isBlank() ? "Unknown Player" : name;
			textureValue = textureValue == null ? "" : textureValue;
			textureSignature = textureSignature == null ? "" : textureSignature;
			kills = Math.max(0, kills);
			level = Math.max(1, level);
			wins = Math.max(0, wins);
			killerWins = Math.max(0, killerWins);
			neutralWins = Math.max(0, neutralWins);
			civilianWins = Math.max(0, civilianWins);
			longestWinStreak = Math.max(0, longestWinStreak);
			longestAliveTicks = Math.max(0L, longestAliveTicks);
		}
	}
}
