package dev.mapselect.network.progression;

import dev.mapselect.MapSelect;
import dev.mapselect.skin.WeaponSkinType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ImportedSkinSettingsPayload(String skinId, String type, String displayName, String context,
                                          float rotationX, float rotationY, float rotationZ,
                                          float translationX, float translationY, float translationZ,
                                          float scaleX, float scaleY, float scaleZ) implements CustomPayload {
	public static final CustomPayload.Id<ImportedSkinSettingsPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "imported_skin_settings"));

	public ImportedSkinSettingsPayload {
		skinId = skinId == null ? "" : skinId;
		type = type == null ? "" : type;
		displayName = displayName == null ? "" : displayName;
		context = context == null ? "" : context;
	}

	public WeaponSkinType skinType() {
		return WeaponSkinType.byId(type);
	}

	public static final PacketCodec<PacketByteBuf, ImportedSkinSettingsPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeString(payload.skinId(), 96);
			buf.writeString(payload.type(), 16);
			buf.writeString(payload.displayName(), 96);
			buf.writeString(payload.context(), 64);
			buf.writeFloat(payload.rotationX());
			buf.writeFloat(payload.rotationY());
			buf.writeFloat(payload.rotationZ());
			buf.writeFloat(payload.translationX());
			buf.writeFloat(payload.translationY());
			buf.writeFloat(payload.translationZ());
			buf.writeFloat(payload.scaleX());
			buf.writeFloat(payload.scaleY());
			buf.writeFloat(payload.scaleZ());
		},
		buf -> new ImportedSkinSettingsPayload(buf.readString(96), buf.readString(16), buf.readString(96),
			buf.readString(64), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
