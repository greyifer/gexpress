package dev.mapselect.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.mapselect.MapSelect;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.preset.train.TrainPresetStorage;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GexpressPresetsSyncHandler {
	private GexpressPresetsSyncHandler() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_PRESET_SAVE_COUNT = 128;
	private static final int MAX_PRESET_JSON_CHARS = 65536;

	public static void register() {
		PayloadTypeRegistry.playS2C().register(GexpressPresetsSyncPayload.ID, GexpressPresetsSyncPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GexpressPresetsSavePayload.ID, GexpressPresetsSavePayload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			if (!canEdit(player)) return;
			server.execute(() -> sendPresetsTo(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(GexpressPresetsSavePayload.ID, (payload, context) -> {
			ServerPlayerEntity sender = context.player();
			MinecraftServer server = sender.getServer();
			if (server == null) return;
			if (!canEdit(sender)) {
				MapSelect.LOGGER.warn("Ignoring gexpress preset save from {} (not OP/host/dev)",
					sender.getName().getString());
				return;
			}
			Map<String, String> copy = new LinkedHashMap<>(payload.presets());
			server.execute(() -> {
				int ok = 0, fail = 0;
				if (copy.size() > MAX_PRESET_SAVE_COUNT) {
					MapSelect.LOGGER.warn("Ignoring oversized gexpress preset save from {} ({} entries)",
						sender.getName().getString(), copy.size());
					return;
				}
				for (Map.Entry<String, String> e : copy.entrySet()) {
					String name = e.getKey();
					if (!PresetStorage.isValidName(name)) { fail++; continue; }
					if (e.getValue() == null || e.getValue().length() > MAX_PRESET_JSON_CHARS) {
						fail++;
						continue;
					}
					try {
						MapPreset preset = GSON.fromJson(e.getValue(), MapPreset.class);
						if (preset == null) { fail++; continue; }
						sanitizePreset(server, preset);
						PresetStorage.save(server, name, preset);
						ok++;
					} catch (Throwable t) {
						MapSelect.LOGGER.warn("Failed to save preset {}: {}", name, t.toString());
						fail++;
					}
				}
				MapSelect.LOGGER.info("G'Express presets updated by {} (saved={}, failed={})",
					sender.getName().getString(), ok, fail);
				broadcastPresets(server);
			});
		});
	}

	public static void broadcastPresets(MinecraftServer server) {
		Map<String, String> snapshot = readAllAsJson(server);
		GexpressPresetsSyncPayload payload = new GexpressPresetsSyncPayload(snapshot);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (canEdit(player)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void sendPresetsTo(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) return;
		Map<String, String> snapshot = readAllAsJson(server);
		ServerPlayNetworking.send(player, new GexpressPresetsSyncPayload(snapshot));
	}

	private static Map<String, String> readAllAsJson(MinecraftServer server) {
		Map<String, String> out = new LinkedHashMap<>();
		try {
			List<String> names = PresetStorage.list(server);
			for (String name : names) {
				try {
					MapPreset p = PresetStorage.load(server, name);
					if (p == null) continue;
					out.put(name, GSON.toJson(p));
				} catch (IOException ioe) {
					MapSelect.LOGGER.warn("Failed to read preset {}: {}", name, ioe.toString());
				}
			}
		} catch (IOException ioe) {
			MapSelect.LOGGER.warn("Failed to list presets: {}", ioe.toString());
		}
		return out;
	}

	private static boolean canEdit(ServerPlayerEntity player) {
		return GexpressPermissions.canEditGameOptions(player);
	}

	private static void sanitizePreset(MinecraftServer server, MapPreset preset) {
		preset.normalize();
		String trainName = preset.defaultTrainPreset;
		if (trainName == null) return;
		try {
			if (!TrainPresetStorage.isValidName(trainName) || !TrainPresetStorage.exists(server, trainName)) {
				preset.defaultTrainPreset = null;
			}
		} catch (IOException e) {
			preset.defaultTrainPreset = null;
		}
	}
}
