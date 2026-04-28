package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Map;

/** C2S push of edited train presets back to the server. */
public record GexpressTrainPresetsSavePayload(Map<String, String> presets) implements CustomPayload {
	public static final CustomPayload.Id<GexpressTrainPresetsSavePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "train_presets_save"));

	public static final PacketCodec<PacketByteBuf, GexpressTrainPresetsSavePayload> CODEC = PacketCodec.tuple(
		PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING, PacketCodecs.STRING),
		GexpressTrainPresetsSavePayload::presets,
		GexpressTrainPresetsSavePayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
