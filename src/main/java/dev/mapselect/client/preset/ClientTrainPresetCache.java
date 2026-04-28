package dev.mapselect.client.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.mapselect.MapSelect;
import dev.mapselect.network.GexpressTrainPresetsSavePayload;
import dev.mapselect.network.GexpressTrainPresetsSyncPayload;
import dev.mapselect.preset.train.TrainPreset;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-side cache of train presets pushed by the server for the train cart editor. */
public final class ClientTrainPresetCache {
	private ClientTrainPresetCache() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, String> rawJsonByName = new LinkedHashMap<>();

	public static void registerClient() {
		ClientPlayNetworking.registerGlobalReceiver(GexpressTrainPresetsSyncPayload.ID, (payload, context) -> {
			Map<String, String> snapshot = new LinkedHashMap<>(payload.presets());
			context.client().execute(() -> {
				rawJsonByName.clear();
				rawJsonByName.putAll(snapshot);
				MapSelect.LOGGER.info("[gexpress] received {} train presets from server", snapshot.size());
			});
		});
	}

	public static Map<String, TrainPreset> snapshot() {
		Map<String, TrainPreset> out = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : rawJsonByName.entrySet()) {
			try {
				TrainPreset preset = GSON.fromJson(e.getValue(), TrainPreset.class);
				if (preset != null) {
					preset.normalize();
					out.put(e.getKey(), preset);
				}
			} catch (Throwable t) {
				MapSelect.LOGGER.debug("Failed to parse train preset {}: {}", e.getKey(), t.toString());
			}
		}
		return out;
	}

	public static Map<String, String> rawSnapshot() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(rawJsonByName));
	}

	public static void savePending(Map<String, TrainPreset> edits) {
		if (edits == null || edits.isEmpty()) return;
		Map<String, String> wire = new LinkedHashMap<>();
		for (Map.Entry<String, TrainPreset> e : edits.entrySet()) {
			if (e.getValue() == null) continue;
			e.getValue().normalize();
			wire.put(e.getKey(), GSON.toJson(e.getValue()));
		}
		if (wire.isEmpty()) return;
		if (!ClientPlayNetworking.canSend(GexpressTrainPresetsSavePayload.ID)) {
			MapSelect.LOGGER.warn("[gexpress] cannot send train preset save - channel not ready");
			return;
		}
		ClientPlayNetworking.send(new GexpressTrainPresetsSavePayload(wire));
	}
}
