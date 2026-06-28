package dev.mapselect.network.role.painter;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PainterDoorwayUsePayload() implements CustomPayload {
	public static final Id<PainterDoorwayUsePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "painter_doorway_use"));
	public static final PacketCodec<PacketByteBuf, PainterDoorwayUsePayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new PainterDoorwayUsePayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
