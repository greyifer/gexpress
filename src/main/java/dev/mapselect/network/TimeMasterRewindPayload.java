package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TimeMasterRewindPayload(int durationTicks) implements CustomPayload {
	public static final CustomPayload.Id<TimeMasterRewindPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "time_master_rewind"));

	public static final PacketCodec<PacketByteBuf, TimeMasterRewindPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeInt(payload.durationTicks()),
		buf -> new TimeMasterRewindPayload(buf.readInt())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
