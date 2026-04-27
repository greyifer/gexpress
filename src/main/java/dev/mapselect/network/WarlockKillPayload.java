package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request to trigger the Warlock's marked-player kill. */
public record WarlockKillPayload() implements CustomPayload {
	public static final CustomPayload.Id<WarlockKillPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "warlock_kill"));

	public static final PacketCodec<PacketByteBuf, WarlockKillPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new WarlockKillPayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
