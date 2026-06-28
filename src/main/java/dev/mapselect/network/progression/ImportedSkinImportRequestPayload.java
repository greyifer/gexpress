package dev.mapselect.network.progression;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ImportedSkinImportRequestPayload() implements CustomPayload {
	public static final CustomPayload.Id<ImportedSkinImportRequestPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "imported_skin_import_request"));

	public static final PacketCodec<PacketByteBuf, ImportedSkinImportRequestPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new ImportedSkinImportRequestPayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
