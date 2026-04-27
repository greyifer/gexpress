package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * S2C push of all map presets to an authorized client (OP / host). Each entry is name → raw
 * JSON of a {@link dev.mapselect.preset.map.MapPreset}. Sent on login and whenever the server
 * writes changes, so the config UI can display the up-to-date values without a blocking fetch.
 */
public record GexpressPresetsSyncPayload(Map<String, String> presets) implements CustomPayload {

	public static final CustomPayload.Id<GexpressPresetsSyncPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "presets_sync"));

	public static final PacketCodec<PacketByteBuf, GexpressPresetsSyncPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING, PacketCodecs.STRING),
		GexpressPresetsSyncPayload::presets,
		GexpressPresetsSyncPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
