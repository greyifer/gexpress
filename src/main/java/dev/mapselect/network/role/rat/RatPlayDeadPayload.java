package dev.mapselect.network.role.rat;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RatPlayDeadPayload() implements CustomPayload {
	public static final Id<RatPlayDeadPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "rat_play_dead"));
	public static final PacketCodec<PacketByteBuf, RatPlayDeadPayload> CODEC =
		PacketCodec.unit(new RatPlayDeadPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
