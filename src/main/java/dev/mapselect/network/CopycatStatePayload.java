package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CopycatStatePayload(boolean active, Identifier copiedRoleId, long remainingTicks,
                                  List<StoredAbility> storedAbilities, int selectedIndex)
		implements CustomPayload {
	public static final CustomPayload.Id<CopycatStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "copycat_state"));

	public CopycatStatePayload {
		remainingTicks = Math.max(0L, remainingTicks);
		storedAbilities = normalize(storedAbilities);
		selectedIndex = storedAbilities.isEmpty() ? 0 : Math.max(0, Math.min(storedAbilities.size() - 1, selectedIndex));
	}

	public static CopycatStatePayload clear() {
		return new CopycatStatePayload(false, null, 0L, List.of(), 0);
	}

	public Identifier storedRoleId() {
		return storedAbilities.isEmpty() ? null
			: storedAbilities.get(Math.max(0, Math.min(storedAbilities.size() - 1, selectedIndex))).roleId();
	}

	public List<Identifier> storedRoleIds() {
		List<Identifier> ids = new ArrayList<>(storedAbilities.size());
		for (StoredAbility ability : storedAbilities) ids.add(ability.roleId());
		return List.copyOf(ids);
	}

	public static final PacketCodec<PacketByteBuf, CopycatStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeBoolean(payload.copiedRoleId() != null);
			if (payload.copiedRoleId() != null) buf.writeIdentifier(payload.copiedRoleId());
			buf.writeLong(payload.remainingTicks());
			buf.writeVarInt(payload.storedAbilities().size());
			for (StoredAbility ability : payload.storedAbilities()) {
				buf.writeIdentifier(ability.roleId());
				buf.writeUuid(ability.sourceId());
				buf.writeString(ability.sourceName(), 64);
			}
			buf.writeVarInt(payload.selectedIndex());
		},
		buf -> {
			boolean active = buf.readBoolean();
			Identifier copiedRoleId = buf.readBoolean() ? buf.readIdentifier() : null;
			long remainingTicks = buf.readLong();
			int size = Math.max(0, Math.min(3, buf.readVarInt()));
			List<StoredAbility> storedAbilities = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				storedAbilities.add(new StoredAbility(buf.readIdentifier(), buf.readUuid(), buf.readString(64)));
			}
			return new CopycatStatePayload(active, copiedRoleId, remainingTicks, storedAbilities, buf.readVarInt());
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static List<StoredAbility> normalize(List<StoredAbility> raw) {
		if (raw == null || raw.isEmpty()) return List.of();
		List<StoredAbility> out = new ArrayList<>(3);
		List<Identifier> seenRoles = new ArrayList<>(3);
		for (StoredAbility ability : raw) {
			if (ability != null && ability.roleId() != null && !seenRoles.contains(ability.roleId())) {
				out.add(ability);
				seenRoles.add(ability.roleId());
			}
			if (out.size() >= 3) break;
		}
		return List.copyOf(out);
	}

	public record StoredAbility(Identifier roleId, UUID sourceId, String sourceName) {
		private static final UUID EMPTY_UUID = new UUID(0L, 0L);

		public StoredAbility {
			sourceId = sourceId == null ? EMPTY_UUID : sourceId;
			sourceName = sourceName == null || sourceName.isBlank() ? "Unknown" : sourceName;
		}
	}
}
