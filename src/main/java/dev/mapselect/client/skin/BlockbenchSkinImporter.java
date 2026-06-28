package dev.mapselect.client.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mapselect.MapSelect;
import dev.mapselect.client.render.DevWeaponModels;
import dev.mapselect.network.progression.ImportedSkinImportRequestPayload;
import dev.mapselect.network.progression.ImportedSkinImportResultPayload;
import dev.mapselect.network.progression.ImportedSkinSettingsPayload;
import dev.mapselect.skin.BlockbenchJavaModelConverter;
import dev.mapselect.skin.WeaponSkin;
import dev.mapselect.skin.WeaponSkinType;
import dev.mapselect.network.progression.ImportedSkinCatalogPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

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
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class BlockbenchSkinImporter {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path INPUT = FabricLoader.getInstance().getConfigDir().resolve("gexpress").resolve("skins");
	private static final Path OVERRIDES = INPUT.resolve("skin_overrides.json");
	private static final Path PACK = FabricLoader.getInstance().getGameDir().resolve("resourcepacks")
		.resolve("gexpress-imported-skins");
	private static final String PACK_ID = "file/gexpress-imported-skins";
	private static List<WeaponSkin.ImportedDefinition> localDefinitions = List.of();
	private static CompletableFuture<ImportResult> pendingServerImport;

	private BlockbenchSkinImporter() {}

	public static ImportResult importAll() {
		List<String> errors = new ArrayList<>();
		List<String> importedIds = new ArrayList<>();
		try {
			Files.createDirectories(INPUT);
			Files.createDirectories(PACK);
			writePackMetadata();
			writeInstructions();
			clearGeneratedAssets();
			clearBuiltInModelOverrides();
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
							MapSelect.LOGGER.warn("Skipped Blockbench skin: {}", error);
							continue;
						}
						manifest.add(imported.manifest());
						definitions.add(imported.definition());
						importedIds.add(imported.definition().id());
					} catch (Throwable t) {
						errors.add(file.getFileName() + ": " + conciseMessage(t));
						MapSelect.LOGGER.warn("Failed to import Blockbench skin {}.", file, t);
					}
				}
			}
			JsonObject root = new JsonObject();
			JsonArray entries = new JsonArray();
			manifest.forEach(entries::add);
			root.add("skins", entries);
			writeJson(PACK.resolve("gexpress_skins.json"), root);
			localDefinitions = List.copyOf(definitions);
			WeaponSkin.replaceImported(definitions);
			WeaponSkin.applyDisplayNames(overrideDisplayNames());
			writeBuiltInModelOverrides();
			if (!manifest.isEmpty()) {
				MapSelect.LOGGER.info("Imported {} Blockbench skin model(s) from {}.", manifest.size(), INPUT);
			}
			return new ImportResult(importedIds, errors, null);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to import Blockbench skins from {}.", INPUT, t);
			return new ImportResult(importedIds, errors, conciseMessage(t));
		}
	}

	public static CompletableFuture<ImportResult> importAndRefresh(MinecraftClient client) {
		if (client != null && client.getNetworkHandler() != null
				&& ClientPlayNetworking.canSend(ImportedSkinImportRequestPayload.ID)) {
			return sendServerRequest(() -> ClientPlayNetworking.send(new ImportedSkinImportRequestPayload()));
		}
		return importLocallyAndRefresh(client);
	}

	private static CompletableFuture<ImportResult> importLocallyAndRefresh(MinecraftClient client) {
		ImportResult result = importAll();
		if (client == null || result.fatalError() != null) return CompletableFuture.completedFuture(result);
		if (client.getNetworkHandler() != null && ClientPlayNetworking.canSend(ImportedSkinCatalogPayload.ID)) {
			ClientPlayNetworking.send(currentCatalogPayload());
		}
		if (!enableGeneratedPack(client)) {
			return CompletableFuture.completedFuture(result.withError("Generated resource pack was not discovered."));
		}
		return refreshSkinModels(client).handle((unused, error) -> {
			return error == null ? result : result.withError("Skin model refresh failed: " + conciseMessage(error));
		});
	}

	public static void completeServerImport(ImportedSkinImportResultPayload payload) {
		CompletableFuture<ImportResult> future = pendingServerImport;
		if (future == null || future.isDone()) return;
		String fatal = payload == null || payload.fatalError().isBlank() ? null : payload.fatalError();
		future.complete(new ImportResult(payload == null ? List.of() : payload.importedIds(),
			payload == null ? List.of() : payload.errors(), fatal));
	}

	public static CompletableFuture<Void> refreshGeneratedModels(MinecraftClient client) {
		if (client == null || !enableGeneratedPack(client)) return CompletableFuture.completedFuture(null);
		return refreshSkinModels(client);
	}

	private static CompletableFuture<Void> refreshSkinModels(MinecraftClient client) {
		return client.reloadResources();
	}

	public static void registerConnectionSync() {
		// Server -> client sync happens from ImportedSkinCatalogSync on join. Uploading local
		// imports automatically here can overwrite the server catalog with an empty local folder.
	}

	public static ImportedSkinCatalogPayload currentCatalogPayload() {
		return ImportedSkinCatalogPayload.current().withAssets(collectGeneratedAssets());
	}

	public static void applyRemoteCatalog(MinecraftClient client, ImportedSkinCatalogPayload payload) {
		if (payload == null) return;
		try {
			writeRemoteAssets(payload.assets());
			WeaponSkin.replaceImported(payload.definitions());
			WeaponSkin.applyDisplayNames(payload.displayNames());
			if (client != null) {
				refreshGeneratedModels(client).exceptionally(error -> {
					MapSelect.LOGGER.warn("Failed to refresh synced imported skin models.", error);
					return null;
				});
			}
		} catch (Throwable error) {
			MapSelect.LOGGER.warn("Failed to apply synced imported skin catalog.", error);
		}
	}

	public static boolean openInputFolder() {
		try {
			Files.createDirectories(INPUT);
			writeInstructions();
			Util.getOperatingSystem().open(INPUT);
			return true;
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to open skin import folder {}.", INPUT, t);
			return false;
		}
	}

	public static Path inputFolder() {
		return INPUT;
	}

	public static List<String> sourceFiles() {
		try {
			Files.createDirectories(INPUT);
			try (Stream<Path> files = Files.list(INPUT)) {
				return files.filter(Files::isRegularFile)
					.map(path -> path.getFileName().toString())
					.filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
					.sorted()
					.toList();
			}
		} catch (IOException ignored) {
			return List.of();
		}
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
		Path modelPath = PACK.resolve("assets/gexpress/models/item/custom_skins").resolve(id + ".json");
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

	public static List<EditableSkin> editableSkins() {
		return Arrays.stream(WeaponSkin.values())
			.flatMap(skin -> Arrays.stream(WeaponSkinType.values())
				.filter(type -> skin.visibleInPicker(type))
				.map(type -> new EditableSkin(skin.id(), skin.displayName(), type,
					DevWeaponModels.skinModelId(skin, type), skin.imported())))
			.filter(skin -> skin.model() != null)
			.sorted(Comparator.comparing(EditableSkin::type).thenComparing(EditableSkin::displayName))
			.toList();
	}

	public static DisplayTransform displayTransform(String skinId, WeaponSkinType type, String context) {
		JsonObject display = generatedDisplay(skinId, type);
		JsonObject transform = display.has(context) && display.get(context).isJsonObject()
			? display.getAsJsonObject(context) : new JsonObject();
		float[] rotation = vector(transform, "rotation", 0.0F);
		float[] translation = vector(transform, "translation", 0.0F);
		float[] scale = vector(transform, "scale", 1.0F);
		return new DisplayTransform(rotation[0], rotation[1], rotation[2], translation[0], translation[1],
			translation[2], scale[0], scale[1], scale[2]);
	}

	public static CompletableFuture<ImportResult> saveSkinSettings(MinecraftClient client, String skinId,
			WeaponSkinType type, String displayName, String context, DisplayTransform transform) {
		if (client != null && client.getNetworkHandler() != null
				&& ClientPlayNetworking.canSend(ImportedSkinSettingsPayload.ID) && type != null && transform != null) {
			ImportedSkinSettingsPayload payload = new ImportedSkinSettingsPayload(skinId, type.id(), displayName, context,
				transform.rotationX(), transform.rotationY(), transform.rotationZ(), transform.translationX(),
				transform.translationY(), transform.translationZ(), transform.scaleX(), transform.scaleY(),
				transform.scaleZ());
			return sendServerRequest(() -> ClientPlayNetworking.send(payload));
		}
		try {
			JsonObject root = readOverrides();
			JsonObject skins = object(root, "skins");
			root.add("skins", skins);
			JsonObject skin = skins.has(skinId) && skins.get(skinId).isJsonObject()
				? skins.getAsJsonObject(skinId) : new JsonObject();
			skins.add(skinId, skin);
			String cleanName = displayName == null ? "" : displayName.replace("|", "").strip();
			if (!cleanName.isBlank()) skin.addProperty("display_name", cleanName);
			if (type != null && context != null && !context.isBlank() && transform != null) {
				JsonObject display = object(skin, "display");
				skin.add("display", display);
				JsonObject typeDisplay = object(display, type.id());
				display.add(type.id(), typeDisplay);
				typeDisplay.add(context, transform.toJson());
			}
			writeJson(OVERRIDES, root);
		} catch (Throwable error) {
			return CompletableFuture.completedFuture(new ImportResult(List.of(), List.of(), conciseMessage(error)));
		}
		return importLocallyAndRefresh(client);
	}

	private static JsonObject generatedDisplay(String skinId, WeaponSkinType type) {
		try {
			EditableSkin skin = editableSkins().stream()
				.filter(entry -> entry.id().equals(skinId) && entry.type() == type).findFirst().orElse(null);
			if (skin == null) return new JsonObject();
			return object(readEffectiveModel(skin.model()), "display");
		} catch (Throwable ignored) {
			return new JsonObject();
		}
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
			MapSelect.LOGGER.warn("Failed to read imported skin overrides {}.", OVERRIDES, error);
			return new JsonObject();
		}
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

	private static Map<String, String> overrideDisplayNames() {
		Map<String, String> names = new java.util.LinkedHashMap<>();
		JsonObject skins = object(readOverrides(), "skins");
		for (Map.Entry<String, JsonElement> entry : skins.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			String name = string(entry.getValue().getAsJsonObject(), "display_name", "").strip();
			if (!name.isBlank()) names.put(entry.getKey(), name);
		}
		return names;
	}

	private static void clearBuiltInModelOverrides() throws IOException {
		for (EditableSkin skin : editableSkins()) {
			if (!skin.imported()) Files.deleteIfExists(generatedModelPath(skin.model()));
		}
	}

	private static void writeBuiltInModelOverrides() throws IOException {
		for (EditableSkin skin : editableSkins()) {
			if (skin.imported()) continue;
			JsonObject override = skinOverride(skin.id());
			if (displayOverrides(override, skin.type()).isEmpty()) continue;
			JsonObject model = readBundledModel(skin.model(), new HashSet<>());
			if (model.isEmpty()) continue;
			mergeDisplayOverrides(model, override, skin.type());
			writeJson(generatedModelPath(skin.model()), model);
		}
	}

	private static JsonObject readEffectiveModel(Identifier modelId) throws IOException {
		Path generated = generatedModelPath(modelId);
		if (Files.isRegularFile(generated)) {
			JsonElement parsed = JsonParser.parseString(Files.readString(generated));
			if (parsed.isJsonObject()) return parsed.getAsJsonObject();
		}
		return readBundledModel(modelId, new HashSet<>());
	}

	private static JsonObject readBundledModel(Identifier modelId, Set<Identifier> visited) throws IOException {
		if (modelId == null || !visited.add(modelId)) return new JsonObject();
		String resource = "assets/" + modelId.getNamespace() + "/models/" + modelId.getPath() + ".json";
		try (InputStream stream = BlockbenchSkinImporter.class.getClassLoader().getResourceAsStream(resource)) {
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
		return PACK.resolve("assets").resolve(modelId.getNamespace()).resolve("models")
			.resolve(modelId.getPath() + ".json");
	}

	private static JsonObject object(JsonObject source, String key) {
		return source != null && source.has(key) && source.get(key).isJsonObject()
			? source.getAsJsonObject(key) : new JsonObject();
	}

	private static float[] vector(JsonObject source, String key, float fallback) {
		float[] values = {fallback, fallback, fallback};
		if (source == null || !source.has(key) || !source.get(key).isJsonArray()) return values;
		JsonArray array = source.getAsJsonArray(key);
		for (int i = 0; i < Math.min(3, array.size()); i++) values[i] = array.get(i).getAsFloat();
		return values;
	}

	private static TextureImport writeTextures(Path modelFile, JsonArray textures, String modelId) throws IOException {
		JsonObject references = new JsonObject();
		Path outputFolder = PACK.resolve("assets/gexpress/textures/item/custom_skins");
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

	private static String uniqueAssetName(String requested, Set<String> usedNames) {
		String base = sanitize(requested);
		if (base.isBlank()) base = "texture";
		String candidate = base;
		for (int suffix = 2; !usedNames.add(candidate); suffix++) candidate = base + "_" + suffix;
		return candidate;
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

	private static void writePackMetadata() throws IOException {
		JsonObject pack = new JsonObject();
		pack.addProperty("pack_format", 34);
		pack.addProperty("description", "G'Express imported Blockbench skins");
		JsonObject root = new JsonObject();
		root.add("pack", pack);
		writeJson(PACK.resolve("pack.mcmeta"), root);
	}

	private static void writeInstructions() throws IOException {
		Path readme = INPUT.resolve("README.txt");
		Files.writeString(readme,
			"Drop Blockbench .bbmodel files here. Include knife, gun, or revolver in each filename.\n"
				+ "Embedded textures are exported as PNG files; adjacent texture files also work.\n"
				+ "In Grey's Express, open Dev > Import Skins and click Import Skins.\n"
				+ "The generated resource pack is enabled automatically and skin menus, cases, and commands refresh.\n",
			StandardCharsets.UTF_8);
	}

	private static void writeJson(Path path, JsonObject value) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, GSON.toJson(value), StandardCharsets.UTF_8);
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
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

	private static boolean enableGeneratedPack(MinecraftClient client) {
		if (client == null || client.options == null || client.getResourcePackManager() == null) return false;
		ResourcePackManager manager = client.getResourcePackManager();
		manager.scanPacks();
		if (!manager.hasProfile(PACK_ID)) return false;
		manager.enable(PACK_ID);
		if (!client.options.resourcePacks.contains(PACK_ID)) client.options.resourcePacks.add(PACK_ID);
		client.options.incompatibleResourcePacks.remove(PACK_ID);
		client.options.write();
		return true;
	}

	private static void clearGeneratedAssets() throws IOException {
		deleteTree(PACK.resolve("assets/gexpress"));
	}

	private static List<ImportedSkinCatalogPayload.Asset> collectGeneratedAssets() {
		if (!Files.isDirectory(PACK)) return List.of();
		List<ImportedSkinCatalogPayload.Asset> assets = new ArrayList<>();
		for (String relativeRoot : List.of("assets/gexpress/models/item", "assets/gexpress/textures/item/custom_skins")) {
			Path root = PACK.resolve(relativeRoot);
			if (!Files.isDirectory(root)) continue;
			try (Stream<Path> paths = Files.walk(root)) {
				for (Path file : paths.filter(Files::isRegularFile)
						.sorted(Comparator.comparing(Path::toString)).toList()) {
					Path relative = PACK.relativize(file);
					String clean = relative.toString().replace('\\', '/');
					if (!isAllowedAssetPath(clean)) continue;
					assets.add(new ImportedSkinCatalogPayload.Asset(clean, Files.readAllBytes(file)));
				}
			} catch (IOException error) {
				MapSelect.LOGGER.warn("Failed to collect generated skin assets from {}.", root, error);
			}
		}
		return List.copyOf(assets);
	}

	private static void writeRemoteAssets(List<ImportedSkinCatalogPayload.Asset> assets) throws IOException {
		Files.createDirectories(PACK);
		writePackMetadata();
		clearGeneratedAssets();
		if (assets == null || assets.isEmpty()) return;
		for (ImportedSkinCatalogPayload.Asset asset : assets) {
			if (asset == null || !isAllowedAssetPath(asset.path()) || asset.bytes().length == 0) continue;
			Path output = PACK.resolve(asset.path()).normalize();
			if (!output.startsWith(PACK.normalize())) continue;
			Files.createDirectories(output.getParent());
			Files.write(output, asset.bytes());
		}
	}

	private static synchronized CompletableFuture<ImportResult> sendServerRequest(Runnable send) {
		if (pendingServerImport != null && !pendingServerImport.isDone()) {
			pendingServerImport.complete(new ImportResult(List.of(), List.of(), "Another skin import request replaced this one."));
		}
		CompletableFuture<ImportResult> future = new CompletableFuture<>();
		pendingServerImport = future;
		future.whenComplete((result, error) -> {
			if (pendingServerImport == future) pendingServerImport = null;
		});
		try {
			send.run();
		} catch (Throwable error) {
			pendingServerImport = null;
			return CompletableFuture.completedFuture(new ImportResult(List.of(), List.of(), conciseMessage(error)));
		}
		return future.completeOnTimeout(new ImportResult(List.of(), List.of(),
			"Timed out waiting for the server skin import result."), 45, TimeUnit.SECONDS);
	}

	private static boolean isAllowedAssetPath(String path) {
		if (path == null || path.isBlank()) return false;
		String clean = path.replace('\\', '/');
		return !clean.startsWith("/") && !clean.contains("..")
			&& (clean.startsWith("assets/gexpress/models/item/")
				|| clean.startsWith("assets/gexpress/textures/item/custom_skins/"));
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
	}

	private static String conciseMessage(Throwable error) {
		Throwable cause = error;
		while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
		String message = cause.getMessage();
		return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
	}

	private record ImportedSkin(JsonObject manifest, WeaponSkin.ImportedDefinition definition) {}
	private record TextureImport(JsonObject references, String primaryTexture) {}

	public record EditableSkin(String id, String displayName, WeaponSkinType type, Identifier model, boolean imported) {}

	public record DisplayTransform(float rotationX, float rotationY, float rotationZ, float translationX,
			float translationY, float translationZ, float scaleX, float scaleY, float scaleZ) {
		private JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.add("rotation", array(rotationX, rotationY, rotationZ));
			json.add("translation", array(translationX, translationY, translationZ));
			json.add("scale", array(scaleX, scaleY, scaleZ));
			return json;
		}

		private static JsonArray array(float x, float y, float z) {
			JsonArray values = new JsonArray();
			values.add(x);
			values.add(y);
			values.add(z);
			return values;
		}
	}

	public record ImportResult(List<String> importedIds, List<String> errors, String fatalError) {
		public ImportResult {
			importedIds = List.copyOf(importedIds == null ? List.of() : importedIds);
			errors = List.copyOf(errors == null ? List.of() : errors);
		}

		public boolean successful() {
			return fatalError == null && errors.isEmpty();
		}

		public ImportResult withError(String error) {
			return new ImportResult(importedIds, errors, error);
		}
	}
}
