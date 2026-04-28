package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Map;

/** S2C push of saved train presets to authorized clients for the train cart editor. */
public record GexpressTrainPresetsSyncPayload(Map<String, String> presets) implements CustomPayload {
	public static final CustomPayload.Id<GexpressTrainPresetsSyncPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "train_presets_sync"));

	public static final PacketCodec<PacketByteBuf, GexpressTrainPresetsSyncPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING, PacketCodecs.STRING),
		GexpressTrainPresetsSyncPayload::presets,
		GexpressTrainPresetsSyncPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
