package dev.mapselect.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mapselect.MapSelect;
import dev.mapselect.network.progression.ImportedSkinCatalogPayload;
import dev.mapselect.network.progression.ImportedSkinImportResultPayload;
import dev.mapselect.network.progression.ImportedSkinSettingsPayload;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ServerSkinImporter {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path INPUT = FabricLoader.getInstance().getConfigDir().resolve("gexpress").resolve("skins");
	private static final Path OVERRIDES = INPUT.resolve("skin_overrides.json");
	private static final Path GENERATED = FabricLoader.getInstance().getConfigDir()
		.resolve("gexpress").resolve("generated-skins");

	private ServerSkinImporter() {}

	public static Path inputFolder() {
		return INPUT;
	}

	public static ImportResult importAll() {
		List<String> errors = new ArrayList<>();
		List<String> importedIds = new ArrayList<>();
		try {
			Files.createDirectories(INPUT);
			Files.createDirectories(GENERATED);
			writeInstructions();
			clearGeneratedAssets();
			List<JsonObject> manifest = new ArrayList<>();
			List<WeaponSkin.ImportedDefinition> definitions = new ArrayList<>();
			try (Stream<Path> files = Files.list(INPUT)) {
				for (Path file : files.filter(Files::isRegularFile)
						.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
						.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
					try {
						ImportedSkin imported = importModel(file);
						if (imported == null) {
							String error = file.getFileName() + ": include knife, gun, or revolver in the filename.";
							errors.add(error);
							MapSelect.LOGGER.warn("Skipped server Blockbench skin: {}", error);
							continue;
						}
						manifest.add(imported.manifest());
						definitions.add(imported.definition());
						importedIds.add(imported.definition().id());
					} catch (Throwable t) {
						errors.add(file.getFileName() + ": " + conciseMessage(t));
						MapSelect.LOGGER.warn("Failed to import server Blockbench skin {}.", file, t);
					}
				}
			}
			JsonObject root = new JsonObject();
			JsonArray entries = new JsonArray();
			manifest.forEach(entries::add);
			root.add("skins", entries);
			writeJson(GENERATED.resolve("gexpress_skins.json"), root);
			writeBuiltInModelOverrides();
			List<ImportedSkinCatalogPayload.Asset> assets = collectGeneratedAssets();
			Map<String, String> displayNames = overrideDisplayNames();
			if (!manifest.isEmpty()) {
				MapSelect.LOGGER.info("Imported {} server Blockbench skin model(s) from {}.", manifest.size(), INPUT);
			}
			return new ImportResult(importedIds, errors, "", INPUT.toString(), definitions, assets, displayNames);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to import server Blockbench skins from {}.", INPUT, t);
			return new ImportResult(importedIds, errors, conciseMessage(t), INPUT.toString(), List.of(), List.of(), Map.of());
		}
	}

	public static ImportResult saveSkinSettings(ImportedSkinSettingsPayload payload) {
		try {
			if (payload == null || payload.skinId().isBlank()) {
				return new ImportResult(List.of(), List.of(), "Missing skin id.", INPUT.toString(), List.of(), List.of(), Map.of());
			}
			JsonObject root = readOverrides();
			JsonObject skins = object(root, "skins");
			root.add("skins", skins);
			JsonObject skin = skins.has(payload.skinId()) && skins.get(payload.skinId()).isJsonObject()
				? skins.getAsJsonObject(payload.skinId()) : new JsonObject();
			skins.add(payload.skinId(), skin);
			String cleanName = payload.displayName().replace("|", "").strip();
			if (!cleanName.isBlank()) skin.addProperty("display_name", cleanName);
			WeaponSkinType type = payload.skinType();
			if (type != null && !payload.context().isBlank()) {
				JsonObject display = object(skin, "display");
				skin.add("display", display);
				JsonObject typeDisplay = object(display, type.id());
				display.add(type.id(), typeDisplay);
				typeDisplay.add(payload.context(), transformJson(payload));
			}
			writeJson(OVERRIDES, root);
		} catch (Throwable error) {
			return new ImportResult(List.of(), List.of(), conciseMessage(error), INPUT.toString(), List.of(), List.of(), Map.of());
		}
		return importAll();
	}

	private static ImportedSkin importModel(Path file) throws IOException {
		String fileName = file.getFileName().toString();
		String id = sanitize(fileName.substring(0, fileName.length() - ".bbmodel".length()));
		if (id.isBlank()) return null;
		WeaponSkinType type = inferredType(id);
		if (type == null) return null;
		JsonElement parsed = JsonParser.parseString(Files.readString(file));
		if (!parsed.isJsonObject()) return null;
		JsonObject project = parsed.getAsJsonObject();
		JsonArray textures = project.has("textures") && project.get("textures").isJsonArray()
			? project.getAsJsonArray("textures") : new JsonArray();
		TextureImport textureImport = writeTextures(file, textures, id);
		JsonObject model = BlockbenchJavaModelConverter.convert(project, textureImport.references(),
			textureImport.primaryTexture());
		JsonObject skinOverride = skinOverride(id);
		mergeDisplayOverrides(model, skinOverride, type);
		Path modelPath = GENERATED.resolve("assets/gexpress/models/item/custom_skins").resolve(id + ".json");
		writeJson(modelPath, model);

		JsonObject manifest = new JsonObject();
		manifest.addProperty("id", id);
		manifest.addProperty("type", type.id());
		manifest.addProperty("model", "gexpress:item/custom_skins/" + id);
		manifest.addProperty("texture", textureImport.primaryTexture());
		Identifier modelId = Identifier.of(MapSelect.MOD_ID, "item/custom_skins/" + id);
		int color = 0x404040 | id.hashCode() & 0xBFBFBF;
		String configuredName = string(skinOverride, "display_name", displayName(id)).strip();
		if (configuredName.isBlank()) configuredName = displayName(id);
		return new ImportedSkin(manifest,
			new WeaponSkin.ImportedDefinition(id, configuredName, color, type, modelId));
	}

	private static void writeBuiltInModelOverrides() throws IOException {
		for (WeaponSkin skin : WeaponSkin.values()) {
			if (skin.imported()) continue;
			for (WeaponSkinType type : WeaponSkinType.values()) {
				if (!skin.visibleInPicker(type)) continue;
				Identifier modelId = skinModelId(skin, type);
				if (modelId == null) continue;
				JsonObject override = skinOverride(skin.id());
				if (displayOverrides(override, type).isEmpty()) continue;
				JsonObject model = readBundledModel(modelId, new HashSet<>());
				if (model.isEmpty()) continue;
				mergeDisplayOverrides(model, override, type);
				writeJson(generatedModelPath(modelId), model);
			}
		}
	}

	private static TextureImport writeTextures(Path modelFile, JsonArray textures, String modelId) throws IOException {
		JsonObject references = new JsonObject();
		Path outputFolder = GENERATED.resolve("assets/gexpress/textures/item/custom_skins");
		if (textures.isEmpty()) {
			String textureId = "gexpress:item/custom_skins/" + modelId;
			Path output = outputFolder.resolve(modelId + ".png");
			if (!writeTexture(modelFile, null, output, true)) {
				throw new IOException("no embedded or adjacent image texture found");
			}
			references.addProperty("layer0", textureId);
			return new TextureImport(references, textureId);
		}

		Set<String> usedNames = new HashSet<>();
		String primary = null;
		for (int i = 0; i < textures.size(); i++) {
			if (!textures.get(i).isJsonObject()) throw new IOException("texture " + i + " is not an object");
			JsonObject texture = textures.get(i).getAsJsonObject();
			String key = string(texture, "id", Integer.toString(i)).replaceFirst("^#", "");
			if (key.isBlank()) key = Integer.toString(i);
			String name = string(texture, "name", key).replaceFirst("(?i)\\.png$", "");
			String suffix = textures.size() == 1 ? "" : "_" + sanitize(name);
			String assetName = uniqueAssetName(modelId + suffix, usedNames);
			String textureId = "gexpress:item/custom_skins/" + assetName;
			Path output = outputFolder.resolve(assetName + ".png");
			if (!writeTexture(modelFile, texture, output, textures.size() == 1)) {
				throw new IOException("texture '" + name + "' has no embedded or adjacent image file");
			}
			references.addProperty(key, textureId);
			String indexKey = Integer.toString(i);
			if (!references.has(indexKey)) references.addProperty(indexKey, textureId);
			if (primary == null) primary = textureId;
		}
		return new TextureImport(references, primary);
	}

	private static boolean writeTexture(Path modelFile, JsonObject texture, Path output,
			boolean allowModelNameFallback) throws IOException {
		String source = texture == null ? "" : string(texture, "source", "");
		int comma = source.indexOf(',');
		if (source.startsWith("data:image/") && source.substring(0, Math.max(0, comma)).endsWith(";base64")
				&& comma >= 0) {
			try {
				writePng(Base64.getDecoder().decode(source.substring(comma + 1)), output);
				return true;
			} catch (IllegalArgumentException error) {
				throw new IOException("embedded texture is not valid base64", error);
			}
		}

		if (texture != null) {
			for (String field : new String[] {"path", "relative_path", "name"}) {
				Path candidate = textureCandidate(modelFile, string(texture, field, ""));
				if (candidate != null && Files.isRegularFile(candidate)) {
					writePng(Files.readAllBytes(candidate), output);
					return true;
				}
			}
		}
		if (!allowModelNameFallback) return false;
		String stem = modelFile.getFileName().toString().replaceFirst("(?i)\\.bbmodel$", "");
		Path adjacent = modelFile.resolveSibling(stem + ".png");
		if (!Files.isRegularFile(adjacent)) return false;
		writePng(Files.readAllBytes(adjacent), output);
		return true;
	}

	private static Path textureCandidate(Path modelFile, String raw) {
		if (raw == null || raw.isBlank() || raw.startsWith("data:")) return null;
		try {
			Path candidate = Path.of(raw);
			if (!candidate.isAbsolute()) candidate = modelFile.getParent().resolve(candidate);
			if (Files.isRegularFile(candidate)) return candidate.normalize();
			if (!raw.toLowerCase(Locale.ROOT).endsWith(".png")) {
				Path png = Path.of(candidate.toString() + ".png");
				if (Files.isRegularFile(png)) return png.normalize();
			}
		} catch (InvalidPathException ignored) {
		}
		return null;
	}

	private static void writePng(byte[] encodedImage, Path output) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(encodedImage));
		if (image == null) throw new IOException("texture is not a supported image");
		Files.createDirectories(output.getParent());
		if (!ImageIO.write(image, "PNG", output.toFile())) throw new IOException("PNG writer is unavailable");
	}

	private static JsonObject skinOverride(String skinId) {
		JsonObject skins = object(readOverrides(), "skins");
		return skins.has(skinId) && skins.get(skinId).isJsonObject()
			? skins.getAsJsonObject(skinId) : new JsonObject();
	}

	private static JsonObject readOverrides() {
		if (!Files.isRegularFile(OVERRIDES)) return new JsonObject();
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(OVERRIDES));
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		} catch (Throwable error) {
			MapSelect.LOGGER.warn("Failed to read server imported skin overrides {}.", OVERRIDES, error);
			return new JsonObject();
		}
	}

	private static Map<String, String> overrideDisplayNames() {
		Map<String, String> names = new LinkedHashMap<>();
		JsonObject skins = object(readOverrides(), "skins");
		for (Map.Entry<String, JsonElement> entry : skins.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			String name = string(entry.getValue().getAsJsonObject(), "display_name", "").strip();
			if (!name.isBlank()) names.put(entry.getKey(), name);
		}
		return names;
	}

	private static void mergeDisplayOverrides(JsonObject model, JsonObject skinOverride, WeaponSkinType type) {
		JsonObject overrides = displayOverrides(skinOverride, type);
		if (overrides.isEmpty()) return;
		JsonObject display = object(model, "display");
		model.add("display", display);
		for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
			if (entry.getValue().isJsonObject()) display.add(entry.getKey(), entry.getValue().deepCopy());
		}
	}

	private static JsonObject displayOverrides(JsonObject skinOverride, WeaponSkinType type) {
		JsonObject display = object(skinOverride, "display");
		if (type != null && display.has(type.id()) && display.get(type.id()).isJsonObject()) {
			return display.getAsJsonObject(type.id());
		}
		for (String context : new String[] {"firstperson_righthand", "firstperson_lefthand",
				"thirdperson_righthand", "thirdperson_lefthand", "gui", "ground", "fixed", "head"}) {
			if (display.has(context)) return display;
		}
		return new JsonObject();
	}

	private static JsonObject readBundledModel(Identifier modelId, Set<Identifier> visited) throws IOException {
		if (modelId == null || !visited.add(modelId)) return new JsonObject();
		String resource = "assets/" + modelId.getNamespace() + "/models/" + modelId.getPath() + ".json";
		try (InputStream stream = ServerSkinImporter.class.getClassLoader().getResourceAsStream(resource)) {
			if (stream == null) return new JsonObject();
			JsonElement parsed = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) return new JsonObject();
			JsonObject child = parsed.getAsJsonObject();
			String parentName = string(child, "parent", "");
			Identifier parentId = parentName.isBlank() ? null : Identifier.tryParse(parentName);
			JsonObject merged = parentId == null ? new JsonObject() : readBundledModel(parentId, visited);
			mergeModelJson(merged, child);
			merged.remove("parent");
			return merged;
		}
	}

	private static void mergeModelJson(JsonObject target, JsonObject source) {
		for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
			if ("textures".equals(entry.getKey()) && entry.getValue().isJsonObject()) {
				JsonObject textures = object(target, "textures");
				target.add("textures", textures);
				for (Map.Entry<String, JsonElement> texture : entry.getValue().getAsJsonObject().entrySet()) {
					textures.add(texture.getKey(), texture.getValue().deepCopy());
				}
			} else {
				target.add(entry.getKey(), entry.getValue().deepCopy());
			}
		}
	}

	private static Path generatedModelPath(Identifier modelId) {
		return GENERATED.resolve("assets").resolve(modelId.getNamespace()).resolve("models")
			.resolve(modelId.getPath() + ".json");
	}

	private static Identifier skinModelId(WeaponSkin skin, WeaponSkinType type) {
		if (skin == null || type == null || !skin.supports(type)) return null;
		Identifier imported = skin.model(type);
		if (imported != null) return imported;
		if (type == WeaponSkinType.KNIFE) {
			if (skin == WeaponSkin.DEV) return Identifier.of(MapSelect.MOD_ID, "item/knife_dev");
			if (skin == WeaponSkin.TRUSTED) return Identifier.of(MapSelect.MOD_ID, "item/knife_trusted");
			if (skin == WeaponSkin.HOST) return Identifier.of(MapSelect.MOD_ID, "item/knife_host");
			return null;
		}
		if (skin == WeaponSkin.DEV) return Identifier.of(MapSelect.MOD_ID, "item/revolver_dev");
		if (skin == WeaponSkin.DEFAULT || skin == WeaponSkin.PASSENGER) {
			return Identifier.of(MapSelect.MOD_ID, "item/revolver_passenger");
		}
		if (skin == WeaponSkin.TRUSTED || skin == WeaponSkin.COLA) return Identifier.of(MapSelect.MOD_ID, "item/revolver_cola");
		if (skin == WeaponSkin.HOST || skin == WeaponSkin.GOLD) return Identifier.of(MapSelect.MOD_ID, "item/revolver_gold");
		if (skin == WeaponSkin.JEM) return Identifier.of(MapSelect.MOD_ID, "item/revolver_jem");
		if (skin == WeaponSkin.BLUE) return Identifier.of(MapSelect.MOD_ID, "item/revolver_blue");
		if (skin == WeaponSkin.PINK) return Identifier.of(MapSelect.MOD_ID, "item/revolver_pink");
		if (skin == WeaponSkin.PLAID) return Identifier.of(MapSelect.MOD_ID, "item/revolver_plaid");
		if (skin == WeaponSkin.BAMBOO) return Identifier.of(MapSelect.MOD_ID, "item/revolver_bamboo");
		if (skin == WeaponSkin.ATOMIZER) return Identifier.of(MapSelect.MOD_ID, "item/revolver_atomizer");
		return null;
	}

	private static List<ImportedSkinCatalogPayload.Asset> collectGeneratedAssets() {
		if (!Files.isDirectory(GENERATED)) return List.of();
		List<ImportedSkinCatalogPayload.Asset> assets = new ArrayList<>();
		for (String relativeRoot : List.of("assets/gexpress/models/item", "assets/gexpress/textures/item/custom_skins")) {
			Path root = GENERATED.resolve(relativeRoot);
			if (!Files.isDirectory(root)) continue;
			try (Stream<Path> paths = Files.walk(root)) {
				for (Path file : paths.filter(Files::isRegularFile)
						.sorted(Comparator.comparing(Path::toString)).toList()) {
					Path relative = GENERATED.relativize(file);
					String clean = relative.toString().replace('\\', '/');
					if (!isAllowedAssetPath(clean)) continue;
					assets.add(new ImportedSkinCatalogPayload.Asset(clean, Files.readAllBytes(file)));
				}
			} catch (IOException error) {
				MapSelect.LOGGER.warn("Failed to collect generated server skin assets from {}.", root, error);
			}
		}
		return List.copyOf(assets);
	}

	private static boolean isAllowedAssetPath(String path) {
		if (path == null || path.isBlank()) return false;
		String clean = path.replace('\\', '/');
		return !clean.startsWith("/") && !clean.contains("..")
			&& (clean.startsWith("assets/gexpress/models/item/")
				|| clean.startsWith("assets/gexpress/textures/item/custom_skins/"));
	}

	private static void clearGeneratedAssets() throws IOException {
		deleteTree(GENERATED.resolve("assets/gexpress"));
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
	}

	private static void writeInstructions() throws IOException {
		Path readme = INPUT.resolve("README.txt");
		Files.writeString(readme,
			"Server-owned G'Express skin import folder.\n"
				+ "Drop Blockbench .bbmodel files here on the server. Include knife, gun, or revolver in each filename.\n"
				+ "Embedded textures are exported as PNG files; adjacent texture files also work.\n"
				+ "In Grey's Express, open Dev > Import Skins and click Import Skins to sync them to every client.\n",
			StandardCharsets.UTF_8);
	}

	private static void writeJson(Path path, JsonObject value) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, GSON.toJson(value), StandardCharsets.UTF_8);
	}

	private static JsonObject object(JsonObject source, String key) {
		return source != null && source.has(key) && source.get(key).isJsonObject()
			? source.getAsJsonObject(key) : new JsonObject();
	}

	private static JsonObject transformJson(ImportedSkinSettingsPayload payload) {
		JsonObject json = new JsonObject();
		json.add("rotation", array(payload.rotationX(), payload.rotationY(), payload.rotationZ()));
		json.add("translation", array(payload.translationX(), payload.translationY(), payload.translationZ()));
		json.add("scale", array(payload.scaleX(), payload.scaleY(), payload.scaleZ()));
		return json;
	}

	private static JsonArray array(float x, float y, float z) {
		JsonArray values = new JsonArray();
		values.add(x);
		values.add(y);
		values.add(z);
		return values;
	}

	private static String uniqueAssetName(String requested, Set<String> usedNames) {
		String base = sanitize(requested);
		if (base.isBlank()) base = "texture";
		String candidate = base;
		for (int suffix = 2; !usedNames.add(candidate); suffix++) candidate = base + "_" + suffix;
		return candidate;
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object != null && object.has(key) && object.get(key).isJsonPrimitive()
			? object.get(key).getAsString() : fallback;
	}

	private static WeaponSkinType inferredType(String id) {
		String words = "_" + id.replace('-', '_') + "_";
		if (words.contains("_knife_")) return WeaponSkinType.KNIFE;
		if (words.contains("_revolver_") || words.contains("_gun_")) return WeaponSkinType.GUN;
		return null;
	}

	private static String sanitize(String raw) {
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_")
			.replaceAll("_+", "_").replaceAll("^[_\\.-]+|[_\\.-]+$", "");
	}

	private static String displayName(String id) {
		String clean = id.replaceFirst("^(knife|revolver|gun)[_-]", "").replace('_', ' ').replace('-', ' ').strip();
		if (clean.isEmpty()) return id;
		StringBuilder out = new StringBuilder(clean.length());
		boolean upper = true;
		for (char c : clean.toCharArray()) {
			out.append(upper ? Character.toUpperCase(c) : c);
			upper = c == ' ';
		}
		return out.toString();
	}

	private static String conciseMessage(Throwable error) {
		Throwable cause = error;
		while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
		String message = cause.getMessage();
		return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
	}

	private record ImportedSkin(JsonObject manifest, WeaponSkin.ImportedDefinition definition) {}
	private record TextureImport(JsonObject references, String primaryTexture) {}

	public record ImportResult(List<String> importedIds, List<String> errors, String fatalError, String importFolder,
	                           List<WeaponSkin.ImportedDefinition> definitions,
	                           List<ImportedSkinCatalogPayload.Asset> assets,
	                           Map<String, String> displayNames) {
		public ImportResult {
			importedIds = List.copyOf(importedIds == null ? List.of() : importedIds);
			errors = List.copyOf(errors == null ? List.of() : errors);
			fatalError = fatalError == null ? "" : fatalError;
			importFolder = importFolder == null ? "" : importFolder;
			definitions = List.copyOf(definitions == null ? List.of() : definitions);
			assets = List.copyOf(assets == null ? List.of() : assets);
			displayNames = Map.copyOf(displayNames == null ? Map.of() : displayNames);
		}

		public boolean fatal() {
			return !fatalError.isBlank();
		}

		public ImportedSkinImportResultPayload payload() {
			return new ImportedSkinImportResultPayload(importedIds, errors, fatalError, importFolder);
		}
	}
}
