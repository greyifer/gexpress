package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request to open the Puppetmaster target menu, or stop active control. */
public record PuppetmasterUsePayload() implements CustomPayload {
	public static final CustomPayload.Id<PuppetmasterUsePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "puppetmaster_use"));

	public static final PacketCodec<PacketByteBuf, PuppetmasterUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new PuppetmasterUsePayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
