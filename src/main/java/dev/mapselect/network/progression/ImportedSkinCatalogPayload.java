package dev.mapselect.network.progression;

import dev.mapselect.MapSelect;
import dev.mapselect.skin.WeaponSkin;
import dev.mapselect.skin.WeaponSkinType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public record ImportedSkinCatalogPayload(List<Entry> entries, List<Asset> assets) implements CustomPayload {
	private static final int MAX_SKINS = 256;
	private static final int MAX_ASSETS = 1024;
	private static final int MAX_ASSET_BYTES = 2 * 1024 * 1024;
	public static final CustomPayload.Id<ImportedSkinCatalogPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "imported_skin_catalog"));

	public ImportedSkinCatalogPayload {
		entries = entries == null ? List.of() : List.copyOf(entries.stream().limit(MAX_SKINS).toList());
		assets = assets == null ? List.of() : List.copyOf(assets.stream().limit(MAX_ASSETS).toList());
	}

	public ImportedSkinCatalogPayload(List<Entry> entries) {
		this(entries, List.of());
	}

	public static ImportedSkinCatalogPayload current() {
		List<Entry> entries = new ArrayList<>(fromDefinitions(WeaponSkin.importedDefinitions()).entries());
		for (WeaponSkin skin : WeaponSkin.values()) {
			if (!skin.imported()) entries.add(new Entry(skin.id(), skin.displayName(), skin.color(), "", ""));
		}
		return new ImportedSkinCatalogPayload(entries);
	}

	public static ImportedSkinCatalogPayload fromDefinitions(List<WeaponSkin.ImportedDefinition> definitions) {
		return new ImportedSkinCatalogPayload((definitions == null ? List.<WeaponSkin.ImportedDefinition>of() : definitions).stream()
			.map(definition -> new Entry(definition.id(), definition.displayName(), definition.color(),
				definition.type().id(), definition.model().toString()))
			.toList());
	}

	public ImportedSkinCatalogPayload withAssets(List<Asset> assets) {
		return new ImportedSkinCatalogPayload(entries, assets);
	}

	public List<WeaponSkin.ImportedDefinition> definitions() {
		List<WeaponSkin.ImportedDefinition> definitions = new ArrayList<>();
		for (Entry entry : entries) {
			WeaponSkinType type = WeaponSkinType.byId(entry.type());
			Identifier model = Identifier.tryParse(entry.model());
			if (type != null && model != null) {
				definitions.add(new WeaponSkin.ImportedDefinition(entry.id(), entry.displayName(), entry.color(), type, model));
			}
		}
		return definitions;
	}

	public Map<String, String> displayNames() {
		Map<String, String> names = new LinkedHashMap<>();
		for (Entry entry : entries) {
			if (!entry.id().isBlank() && !entry.displayName().isBlank()) names.put(entry.id(), entry.displayName());
		}
		return names;
	}

	public static final PacketCodec<PacketByteBuf, ImportedSkinCatalogPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(payload.entries().size());
			for (Entry entry : payload.entries()) {
				buf.writeString(entry.id(), 96);
				buf.writeString(entry.displayName(), 96);
				buf.writeInt(entry.color());
				buf.writeString(entry.type(), 16);
				buf.writeString(entry.model(), 160);
			}
			buf.writeVarInt(payload.assets().size());
			for (Asset asset : payload.assets()) {
				buf.writeString(asset.path(), 240);
				buf.writeByteArray(asset.bytes());
			}
		},
		buf -> {
			int size = Math.max(0, Math.min(MAX_SKINS, buf.readVarInt()));
			List<Entry> entries = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				entries.add(new Entry(buf.readString(96), buf.readString(96), buf.readInt(),
					buf.readString(16), buf.readString(160)));
			}
			if (buf.readableBytes() <= 0) return new ImportedSkinCatalogPayload(entries);
			int assetSize = Math.max(0, Math.min(MAX_ASSETS, buf.readVarInt()));
			List<Asset> assets = new ArrayList<>(assetSize);
			for (int i = 0; i < assetSize; i++) {
				assets.add(new Asset(buf.readString(240), buf.readByteArray(MAX_ASSET_BYTES)));
			}
			return new ImportedSkinCatalogPayload(entries, assets);
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record Entry(String id, String displayName, int color, String type, String model) {
		public Entry {
			id = id == null ? "" : id;
			displayName = displayName == null ? "" : displayName;
			type = type == null ? "" : type;
			model = model == null ? "" : model;
		}
	}

	public record Asset(String path, byte[] bytes) {
		public Asset {
			path = path == null ? "" : path.replace('\\', '/');
			bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, Math.min(bytes.length, MAX_ASSET_BYTES));
		}
	}
}
