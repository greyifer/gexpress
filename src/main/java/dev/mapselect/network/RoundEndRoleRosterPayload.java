package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Server -> client snapshot of exact round roles for the extra end-screen roster. */
public record RoundEndRoleRosterPayload(Map<UUID, String> roleIds, Map<UUID, Integer> levels,
                                        Map<UUID, String> names, Set<UUID> spectators) implements CustomPayload {
	public static final CustomPayload.Id<RoundEndRoleRosterPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "round_end_role_roster"));

	public RoundEndRoleRosterPayload {
		roleIds = roleIds == null ? Map.of() : Map.copyOf(roleIds);
		levels = levels == null ? Map.of() : Map.copyOf(levels);
		names = names == null ? Map.of() : Map.copyOf(names);
		spectators = spectators == null ? Set.of() : Set.copyOf(spectators);
	}

	public static final PacketCodec<PacketByteBuf, RoundEndRoleRosterPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(payload.roleIds().size());
			for (Map.Entry<UUID, String> entry : payload.roleIds().entrySet()) {
				buf.writeUuid(entry.getKey());
				buf.writeString(entry.getValue() == null ? "" : entry.getValue(), 96);
				buf.writeVarInt(Math.max(1, payload.levels().getOrDefault(entry.getKey(), 1)));
				buf.writeString(payload.names().getOrDefault(entry.getKey(), ""), 32);
				buf.writeBoolean(payload.spectators().contains(entry.getKey()));
			}
		},
		buf -> {
			int size = Math.max(0, Math.min(128, buf.readVarInt()));
			Map<UUID, String> roles = new LinkedHashMap<>();
			Map<UUID, Integer> levels = new LinkedHashMap<>();
			Map<UUID, String> names = new LinkedHashMap<>();
			Set<UUID> spectators = new LinkedHashSet<>();
			for (int i = 0; i < size; i++) {
				UUID playerId = buf.readUuid();
				roles.put(playerId, buf.readString(96));
				levels.put(playerId, Math.max(1, buf.readVarInt()));
				names.put(playerId, buf.readString(32));
				if (buf.readBoolean()) spectators.add(playerId);
			}
			return new RoundEndRoleRosterPayload(roles, levels, names, spectators);
		}
	);

	public static RoundEndRoleRosterPayload clear() {
		return new RoundEndRoleRosterPayload(Map.of(), Map.of(), Map.of(), Set.of());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
