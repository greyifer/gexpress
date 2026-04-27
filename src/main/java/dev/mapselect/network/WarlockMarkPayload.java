package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request to mark the player the Warlock is looking at. */
public record WarlockMarkPayload() implements CustomPayload {
	public static final CustomPayload.Id<WarlockMarkPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "warlock_mark"));

	public static final PacketCodec<PacketByteBuf, WarlockMarkPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new WarlockMarkPayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
