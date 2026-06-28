package dev.mapselect.network.coinbarrier;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record CoinBarrierPurchasePayload(BlockPos pos) implements CustomPayload {
	public static final Id<CoinBarrierPurchasePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "coin_barrier_purchase"));

	public static final PacketCodec<PacketByteBuf, CoinBarrierPurchasePayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeBlockPos(payload.pos()),
		buf -> new CoinBarrierPurchasePayload(buf.readBlockPos())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
