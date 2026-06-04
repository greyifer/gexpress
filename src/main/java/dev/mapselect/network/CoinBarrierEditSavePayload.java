package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record CoinBarrierEditSavePayload(BlockPos pos, int price, String title) implements CustomPayload {
	public static final Id<CoinBarrierEditSavePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "coin_barrier_edit_save"));

	public CoinBarrierEditSavePayload {
		title = title == null ? "" : title;
	}

	public static final PacketCodec<PacketByteBuf, CoinBarrierEditSavePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBlockPos(payload.pos());
			buf.writeInt(payload.price());
			buf.writeString(payload.title(), 64);
		},
		buf -> new CoinBarrierEditSavePayload(buf.readBlockPos(), buf.readInt(), buf.readString(64))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
