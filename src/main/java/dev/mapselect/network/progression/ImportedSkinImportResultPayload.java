package dev.mapselect.network.progression;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ImportedSkinImportResultPayload(List<String> importedIds, List<String> errors, String fatalError,
                                              String importFolder) implements CustomPayload {
	private static final int MAX_IDS = 256;
	private static final int MAX_ERRORS = 32;

	public static final CustomPayload.Id<ImportedSkinImportResultPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "imported_skin_import_result"));

	public ImportedSkinImportResultPayload {
		importedIds = List.copyOf((importedIds == null ? List.<String>of() : importedIds).stream()
			.limit(MAX_IDS).map(value -> value == null ? "" : value).toList());
		errors = List.copyOf((errors == null ? List.<String>of() : errors).stream()
			.limit(MAX_ERRORS).map(value -> value == null ? "" : value).toList());
		fatalError = fatalError == null ? "" : fatalError;
		importFolder = importFolder == null ? "" : importFolder;
	}

	public boolean fatal() {
		return !fatalError.isBlank();
	}

	public static final PacketCodec<PacketByteBuf, ImportedSkinImportResultPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(payload.importedIds().size());
			for (String id : payload.importedIds()) buf.writeString(id, 96);
			buf.writeVarInt(payload.errors().size());
			for (String error : payload.errors()) buf.writeString(error, 240);
			buf.writeString(payload.fatalError(), 240);
			buf.writeString(payload.importFolder(), 260);
		},
		buf -> {
			int idCount = Math.max(0, Math.min(MAX_IDS, buf.readVarInt()));
			List<String> ids = new ArrayList<>(idCount);
			for (int i = 0; i < idCount; i++) ids.add(buf.readString(96));
			int errorCount = Math.max(0, Math.min(MAX_ERRORS, buf.readVarInt()));
			List<String> errors = new ArrayList<>(errorCount);
			for (int i = 0; i < errorCount; i++) errors.add(buf.readString(240));
			return new ImportedSkinImportResultPayload(ids, errors, buf.readString(240), buf.readString(260));
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
