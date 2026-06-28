package dev.mapselect.network.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.mapselect.MapSelect;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.skin.ServerSkinImporter;
import dev.mapselect.skin.WeaponSkin;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ImportedSkinCatalogSync {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CATALOG_PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("gexpress").resolve("imported_skin_catalog.json");
	private static List<ImportedSkinCatalogPayload.Asset> acceptedAssets = List.of();

	private ImportedSkinCatalogSync() {}

	public static void register() {
		loadCatalog();
		PayloadTypeRegistry.playC2S().register(ImportedSkinCatalogPayload.ID, ImportedSkinCatalogPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ImportedSkinImportRequestPayload.ID, ImportedSkinImportRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ImportedSkinSettingsPayload.ID, ImportedSkinSettingsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ImportedSkinCatalogPayload.ID, ImportedSkinCatalogPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ImportedSkinImportResultPayload.ID, ImportedSkinImportResultPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ImportedSkinCatalogPayload.ID, (payload, context) ->
			context.server().execute(() -> accept(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(ImportedSkinImportRequestPayload.ID, (payload, context) ->
			context.server().execute(() -> importFromServerFolder(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(ImportedSkinSettingsPayload.ID, (payload, context) ->
			context.server().execute(() -> saveSettingsAndImport(context.player(), payload)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			ImportedSkinCatalogPayload current = currentPayload();
			if (!current.entries().isEmpty()) send(handler.player, current);
		}));
	}

	private static void importFromServerFolder(ServerPlayerEntity sender) {
		if (!canManageSkins(sender)) return;
		publishImport(sender, ServerSkinImporter.importAll());
	}

	private static void saveSettingsAndImport(ServerPlayerEntity sender, ImportedSkinSettingsPayload payload) {
		if (!canManageSkins(sender)) return;
		publishImport(sender, ServerSkinImporter.saveSkinSettings(payload));
	}

	private static boolean canManageSkins(ServerPlayerEntity sender) {
		if (sender != null && GexpressPermissions.canManageSkins(sender)) return true;
		MapSelect.LOGGER.warn("Rejected imported skin management request from unauthorized player {}.",
			sender == null ? "unknown" : sender.getName().getString());
		sendResult(sender, new ImportedSkinImportResultPayload(List.of(), List.of(),
			"You do not have permission to import or edit skins.", ServerSkinImporter.inputFolder().toString()));
		return false;
	}

	private static void publishImport(ServerPlayerEntity sender, ServerSkinImporter.ImportResult result) {
		if (result == null) {
			sendResult(sender, new ImportedSkinImportResultPayload(List.of(), List.of(),
				"Server import failed without a result.", ServerSkinImporter.inputFolder().toString()));
			return;
		}
		if (!result.fatal()) {
			WeaponSkin.replaceImported(result.definitions());
			WeaponSkin.applyDisplayNames(result.displayNames());
			acceptedAssets = sanitizeAssets(result.assets());
			ImportedSkinCatalogPayload accepted = currentPayload();
			saveCatalog(accepted);
			if (sender != null && sender.getServer() != null) {
				for (ServerPlayerEntity player : sender.getServer().getPlayerManager().getPlayerList()) send(player, accepted);
			}
			MapSelect.LOGGER.info("Imported {} server-owned weapon skin(s) from {}.",
				result.importedIds().size(), result.importFolder());
		}
		sendResult(sender, result.payload());
	}

	private static void accept(ServerPlayerEntity sender, ImportedSkinCatalogPayload payload) {
		if (sender == null || !GexpressPermissions.canManageSkins(sender)) {
			MapSelect.LOGGER.warn("Rejected imported skin catalog from unauthorized player {}.",
				sender == null ? "unknown" : sender.getName().getString());
			return;
		}
		List<WeaponSkin.ImportedDefinition> definitions = payload.definitions();
		List<ImportedSkinCatalogPayload.Asset> sanitizedAssets = sanitizeAssets(payload.assets());
		boolean containsImportedSkinAssets = sanitizedAssets.stream().anyMatch(ImportedSkinCatalogSync::isImportedSkinAsset);
		if (!definitions.isEmpty() || containsImportedSkinAssets) {
			WeaponSkin.replaceImported(definitions);
			acceptedAssets = sanitizedAssets;
		} else {
			acceptedAssets = mergeNonImportedAssets(acceptedAssets, sanitizedAssets);
		}
		WeaponSkin.applyDisplayNames(payload.displayNames());
		ImportedSkinCatalogPayload accepted = currentPayload();
		saveCatalog(accepted);
		for (ServerPlayerEntity player : sender.getServer().getPlayerManager().getPlayerList()) send(player, accepted);
		MapSelect.LOGGER.info("Accepted {} imported weapon skin(s) from {}.", accepted.entries().size(),
			sender.getName().getString());
	}

	private static void loadCatalog() {
		if (!Files.isRegularFile(CATALOG_PATH)) return;
		try {
			StoredCatalog stored = GSON.fromJson(Files.readString(CATALOG_PATH, StandardCharsets.UTF_8), StoredCatalog.class);
			ImportedSkinCatalogPayload payload = new ImportedSkinCatalogPayload(
				stored == null || stored.entries() == null ? List.of() : stored.entries(),
				stored == null || stored.assets() == null ? List.of() : stored.assets());
			WeaponSkin.replaceImported(payload.definitions());
			WeaponSkin.applyDisplayNames(payload.displayNames());
			acceptedAssets = sanitizeAssets(payload.assets());
			MapSelect.LOGGER.info("Loaded {} imported weapon skin(s) from {}.", payload.entries().size(), CATALOG_PATH);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to load imported skin catalog {}.", CATALOG_PATH, t);
		}
	}

	private static void saveCatalog(ImportedSkinCatalogPayload payload) {
		try {
			Files.createDirectories(CATALOG_PATH.getParent());
			Files.writeString(CATALOG_PATH, GSON.toJson(new StoredCatalog(payload.entries(), payload.assets())),
				StandardCharsets.UTF_8);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to save imported skin catalog {}.", CATALOG_PATH, t);
		}
	}

	private static void send(ServerPlayerEntity player, ImportedSkinCatalogPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, ImportedSkinCatalogPayload.ID)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static void sendResult(ServerPlayerEntity player, ImportedSkinImportResultPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, ImportedSkinImportResultPayload.ID)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static ImportedSkinCatalogPayload currentPayload() {
		return ImportedSkinCatalogPayload.current().withAssets(acceptedAssets);
	}

	private static List<ImportedSkinCatalogPayload.Asset> sanitizeAssets(List<ImportedSkinCatalogPayload.Asset> assets) {
		if (assets == null || assets.isEmpty()) return List.of();
		List<ImportedSkinCatalogPayload.Asset> out = new ArrayList<>();
		for (ImportedSkinCatalogPayload.Asset asset : assets) {
			if (asset == null || !isAllowedAssetPath(asset.path()) || asset.bytes().length == 0) continue;
			out.add(asset);
		}
		return List.copyOf(out);
	}

	private static boolean isAllowedAssetPath(String path) {
		if (path == null || path.isBlank()) return false;
		String clean = path.replace('\\', '/');
		return !clean.startsWith("/") && !clean.contains("..")
			&& (clean.startsWith("assets/gexpress/models/item/")
				|| clean.startsWith("assets/gexpress/textures/item/custom_skins/"));
	}

	private static boolean isImportedSkinAsset(ImportedSkinCatalogPayload.Asset asset) {
		if (asset == null) return false;
		String path = asset.path().replace('\\', '/');
		return path.startsWith("assets/gexpress/models/item/custom_skins/")
			|| path.startsWith("assets/gexpress/textures/item/custom_skins/");
	}

	private static List<ImportedSkinCatalogPayload.Asset> mergeNonImportedAssets(
			List<ImportedSkinCatalogPayload.Asset> existing, List<ImportedSkinCatalogPayload.Asset> incoming) {
		if (incoming == null || incoming.isEmpty()) return existing == null ? List.of() : List.copyOf(existing);
		List<ImportedSkinCatalogPayload.Asset> merged = new ArrayList<>();
		if (existing != null) {
			for (ImportedSkinCatalogPayload.Asset asset : existing) {
				if (asset != null && isImportedSkinAsset(asset)) merged.add(asset);
			}
		}
		for (ImportedSkinCatalogPayload.Asset asset : incoming) {
			if (asset != null && !isImportedSkinAsset(asset)) merged.add(asset);
		}
		return List.copyOf(merged);
	}

	private record StoredCatalog(List<ImportedSkinCatalogPayload.Entry> entries,
	                             List<ImportedSkinCatalogPayload.Asset> assets) {}
}
