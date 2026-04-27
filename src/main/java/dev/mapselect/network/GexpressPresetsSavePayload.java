package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * C2S push of edited map presets back to the server. Server authorizes (OP or host), writes
 * each entry to {@code <world>/gexpress/<name>.json}, then re-broadcasts the resulting state
 * to authorized clients via {@link GexpressPresetsSyncPayload} so every open menu refreshes.
 */
public record GexpressPresetsSavePayload(Map<String, String> presets) implements CustomPayload {

	public static final CustomPayload.Id<GexpressPresetsSavePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "presets_save"));

	public static final PacketCodec<PacketByteBuf, GexpressPresetsSavePayload> CODEC = PacketCodec.tuple(
		PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING, PacketCodecs.STRING),
		GexpressPresetsSavePayload::presets,
		GexpressPresetsSavePayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
