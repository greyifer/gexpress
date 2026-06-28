package dev.mapselect.network.coinbarrier;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record CoinBarrierPurchaseOpenPayload(BlockPos pos, int price, String title) implements CustomPayload {
	public static final Id<CoinBarrierPurchaseOpenPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "coin_barrier_purchase_open"));

	public CoinBarrierPurchaseOpenPayload {
		title = title == null ? "" : title;
	}

	public static final PacketCodec<PacketByteBuf, CoinBarrierPurchaseOpenPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBlockPos(payload.pos());
			buf.writeInt(payload.price());
			buf.writeString(payload.title(), 64);
		},
		buf -> new CoinBarrierPurchaseOpenPayload(buf.readBlockPos(), buf.readInt(), buf.readString(64))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
