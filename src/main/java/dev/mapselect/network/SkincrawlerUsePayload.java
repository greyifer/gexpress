package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SkincrawlerUsePayload() implements CustomPayload {
	public static final Id<SkincrawlerUsePayload> ID = new Id<>(Identifier.of(MapSelect.MOD_ID, "skincrawler_use"));
	public static final PacketCodec<PacketByteBuf, SkincrawlerUsePayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new SkincrawlerUsePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
