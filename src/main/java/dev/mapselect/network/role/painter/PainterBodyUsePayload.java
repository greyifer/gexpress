package dev.mapselect.network.role.painter;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PainterBodyUsePayload() implements CustomPayload {
	public static final Id<PainterBodyUsePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "painter_body_use"));
	public static final PacketCodec<PacketByteBuf, PainterBodyUsePayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new PainterBodyUsePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
