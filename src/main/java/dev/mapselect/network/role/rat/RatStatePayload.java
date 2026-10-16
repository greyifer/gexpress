package dev.mapselect.network.role.rat;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RatStatePayload(boolean active, int invisibilityTicks, int maxInvisibilityTicks,
		boolean invisible, boolean playingDead, int infectedPlayers, int requiredPlayers,
		List<UUID> infectedPlayerIds) implements CustomPayload {
	public static final Id<RatStatePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "rat_state"));

	public RatStatePayload {
		invisibilityTicks = Math.max(0, invisibilityTicks);
		maxInvisibilityTicks = Math.max(1, maxInvisibilityTicks);
		infectedPlayers = Math.max(0, infectedPlayers);
		requiredPlayers = Math.max(0, requiredPlayers);
		infectedPlayerIds = infectedPlayerIds == null ? List.of() : List.copyOf(infectedPlayerIds);
	}

	public static RatStatePayload clear() {
		return new RatStatePayload(false, 0, 1, false, false, 0, 0, List.of());
	}

	public static final PacketCodec<PacketByteBuf, RatStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeInt(payload.invisibilityTicks());
			buf.writeInt(payload.maxInvisibilityTicks());
			buf.writeBoolean(payload.invisible());
			buf.writeBoolean(payload.playingDead());
			buf.writeInt(payload.infectedPlayers());
			buf.writeInt(payload.requiredPlayers());
			buf.writeInt(payload.infectedPlayerIds().size());
			for (UUID playerId : payload.infectedPlayerIds()) buf.writeUuid(playerId);
		},
		buf -> {
			boolean active = buf.readBoolean();
			int invisibilityTicks = buf.readInt();
			int maxInvisibilityTicks = buf.readInt();
			boolean invisible = buf.readBoolean();
			boolean playingDead = buf.readBoolean();
			int infectedPlayers = buf.readInt();
			int requiredPlayers = buf.readInt();
			int count = Math.max(0, Math.min(256, buf.readInt()));
			List<UUID> infectedPlayerIds = new ArrayList<>(count);
			for (int index = 0; index < count; index++) infectedPlayerIds.add(buf.readUuid());
			return new RatStatePayload(active, invisibilityTicks, maxInvisibilityTicks,
				invisible, playingDead, infectedPlayers, requiredPlayers, infectedPlayerIds);
		}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
