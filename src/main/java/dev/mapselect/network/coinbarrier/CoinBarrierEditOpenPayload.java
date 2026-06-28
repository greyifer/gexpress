package dev.mapselect.network.coinbarrier;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record CoinBarrierEditOpenPayload(BlockPos pos, int price, String title) implements CustomPayload {
	public static final Id<CoinBarrierEditOpenPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "coin_barrier_edit_open"));

	public CoinBarrierEditOpenPayload {
		title = title == null ? "" : title;
	}

	public static final PacketCodec<PacketByteBuf, CoinBarrierEditOpenPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBlockPos(payload.pos());
			buf.writeInt(payload.price());
			buf.writeString(payload.title(), 64);
		},
		buf -> new CoinBarrierEditOpenPayload(buf.readBlockPos(), buf.readInt(), buf.readString(64))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
