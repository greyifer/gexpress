package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ClaimLevelRewardPayload(int level) implements CustomPayload {
	public static final CustomPayload.Id<ClaimLevelRewardPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "claim_level_reward"));

	public static final PacketCodec<PacketByteBuf, ClaimLevelRewardPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeInt(payload.level()),
		buf -> new ClaimLevelRewardPayload(buf.readInt())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
