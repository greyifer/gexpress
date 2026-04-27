package dev.mapselect.client.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.mapselect.MapSelect;
import dev.mapselect.network.GexpressPresetsSavePayload;
import dev.mapselect.network.GexpressPresetsSyncPayload;
import dev.mapselect.preset.map.MapPreset;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side cache of map presets pushed by the server. Populated on login and refreshed
 * after every server-side save. The YACL config menu reads from here synchronously when the
 * user opens the map tab and writes back through {@link #savePending}.
 */
public final class ClientPresetCache {
	private ClientPresetCache() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Map<String, String> rawJsonByName = new LinkedHashMap<>();

	public static void registerClient() {
		ClientPlayNetworking.registerGlobalReceiver(GexpressPresetsSyncPayload.ID, (payload, context) -> {
			Map<String, String> snapshot = new LinkedHashMap<>(payload.presets());
			context.client().execute(() -> {
				rawJsonByName.clear();
				rawJsonByName.putAll(snapshot);
				MapSelect.LOGGER.info("[gexpress] received {} presets from server", snapshot.size());
			});
		});
	}

	public static Map<String, MapPreset> snapshot() {
		Map<String, MapPreset> out = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : rawJsonByName.entrySet()) {
			try {
				MapPreset p = GSON.fromJson(e.getValue(), MapPreset.class);
				if (p != null) out.put(e.getKey(), p);
			} catch (Throwable t) {
				MapSelect.LOGGER.debug("Failed to parse preset {}: {}", e.getKey(), t.toString());
			}
		}
		return out;
	}

	public static Map<String, String> rawSnapshot() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(rawJsonByName));
	}

	public static void savePending(Map<String, MapPreset> edits) {
		if (edits == null || edits.isEmpty()) return;
		Map<String, String> wire = new LinkedHashMap<>();
		for (Map.Entry<String, MapPreset> e : edits.entrySet()) {
			if (e.getValue() == null) continue;
			wire.put(e.getKey(), GSON.toJson(e.getValue()));
		}
		if (wire.isEmpty()) return;
		if (!ClientPlayNetworking.canSend(GexpressPresetsSavePayload.ID)) {
			MapSelect.LOGGER.warn("[gexpress] cannot send preset save — channel not ready");
			return;
		}
		ClientPlayNetworking.send(new GexpressPresetsSavePayload(wire));
	}
}
