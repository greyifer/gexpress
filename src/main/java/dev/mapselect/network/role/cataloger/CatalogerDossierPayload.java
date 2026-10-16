package dev.mapselect.network.role.cataloger;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CatalogerDossierPayload(boolean active, List<Entry> passengers,
		int evilCount, int civilianCount, String exactRoleNamespace,
		String exactRolePath) implements CustomPayload {
	public static final Id<CatalogerDossierPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "cataloger_dossier"));

	public CatalogerDossierPayload {
		passengers = passengers == null ? List.of() : List.copyOf(passengers);
		evilCount = Math.max(0, Math.min(5, evilCount));
		civilianCount = Math.max(0, Math.min(5, civilianCount));
		exactRoleNamespace = exactRoleNamespace == null ? "" : exactRoleNamespace;
		exactRolePath = exactRolePath == null ? "" : exactRolePath;
	}

	public static final PacketCodec<PacketByteBuf, CatalogerDossierPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeInt(payload.passengers().size());
			for (Entry entry : payload.passengers()) {
				buf.writeUuid(entry.playerId());
				buf.writeString(entry.playerName());
			}
			buf.writeInt(payload.evilCount());
			buf.writeInt(payload.civilianCount());
			buf.writeString(payload.exactRoleNamespace());
			buf.writeString(payload.exactRolePath());
		},
		buf -> {
			boolean active = buf.readBoolean();
			int count = Math.max(0, Math.min(5, buf.readInt()));
			List<Entry> passengers = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				passengers.add(new Entry(buf.readUuid(), buf.readString(64)));
			}
			return new CatalogerDossierPayload(active, passengers,
				buf.readInt(), buf.readInt(), buf.readString(64), buf.readString(128));
		}
	);

	public static CatalogerDossierPayload clear() {
		return new CatalogerDossierPayload(false, List.of(), 0, 0, "", "");
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record Entry(UUID playerId, String playerName) {
		public Entry {
			playerName = playerName == null ? "" : playerName;
		}
	}
}
