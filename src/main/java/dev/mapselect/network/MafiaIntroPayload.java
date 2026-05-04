package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MafiaIntroPayload(int durationTicks) implements CustomPayload {
	public static final Id<MafiaIntroPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "mafia_intro"));

	public MafiaIntroPayload {
		durationTicks = Math.max(0, durationTicks);
	}

	public static final PacketCodec<PacketByteBuf, MafiaIntroPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeInt(payload.durationTicks()),
		buf -> new MafiaIntroPayload(buf.readInt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
