package dev.mapselect.network.floatingtext;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record FloatingTextEditOpenPayload(BlockPos pos, String text) implements CustomPayload {
	public static final Id<FloatingTextEditOpenPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "floating_text_edit_open"));
	public static final PacketCodec<PacketByteBuf, FloatingTextEditOpenPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBlockPos(payload.pos());
			buf.writeString(payload.text(), 256);
		},
		buf -> new FloatingTextEditOpenPayload(buf.readBlockPos(), buf.readString(256))
	);

	public FloatingTextEditOpenPayload {
		text = text == null ? "" : text;
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
