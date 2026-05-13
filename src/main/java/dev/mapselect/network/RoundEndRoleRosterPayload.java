package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server -> client snapshot of exact round roles for the extra end-screen roster. */
public record RoundEndRoleRosterPayload(Map<UUID, String> roleIds) implements CustomPayload {
	public static final CustomPayload.Id<RoundEndRoleRosterPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "round_end_role_roster"));

	public RoundEndRoleRosterPayload {
		roleIds = roleIds == null ? Map.of() : Map.copyOf(roleIds);
	}

	public static final PacketCodec<PacketByteBuf, RoundEndRoleRosterPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(payload.roleIds().size());
			for (Map.Entry<UUID, String> entry : payload.roleIds().entrySet()) {
				buf.writeUuid(entry.getKey());
				buf.writeString(entry.getValue() == null ? "" : entry.getValue(), 96);
			}
		},
		buf -> {
			int size = Math.max(0, Math.min(128, buf.readVarInt()));
			Map<UUID, String> roles = new LinkedHashMap<>();
			for (int i = 0; i < size; i++) {
				roles.put(buf.readUuid(), buf.readString(96));
			}
			return new RoundEndRoleRosterPayload(roles);
		}
	);

	public static RoundEndRoleRosterPayload clear() {
		return new RoundEndRoleRosterPayload(Map.of());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
