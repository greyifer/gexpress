package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Optional Puppetmaster settings that were added after the main config packet shape. */
public record PuppetmasterConfigPayload(boolean canKillOwnBody) implements CustomPayload {
	public static final CustomPayload.Id<PuppetmasterConfigPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "puppetmaster_config"));

	public static final PacketCodec<PacketByteBuf, PuppetmasterConfigPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.BOOL, PuppetmasterConfigPayload::canKillOwnBody,
		PuppetmasterConfigPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
