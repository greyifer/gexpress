package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client, per-player: "you currently have the G'Express Night Vision modifier". */
public record NightVisionSyncPayload(boolean nightVision) implements CustomPayload {
	public static final CustomPayload.Id<NightVisionSyncPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "night_vision_sync"));

	public static final PacketCodec<PacketByteBuf, NightVisionSyncPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.BOOL, NightVisionSyncPayload::nightVision,
		NightVisionSyncPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
