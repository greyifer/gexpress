package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MafiaActionPayload(int action) implements CustomPayload {
	public static final int RECRUIT_MAFIOSO = 0;
	public static final int RECRUIT_JANITOR = 1;
	public static final int CLEAN_BODY = 2;

	public static final Id<MafiaActionPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "mafia_action"));

	public static final PacketCodec<PacketByteBuf, MafiaActionPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeInt(payload.action()),
		buf -> new MafiaActionPayload(buf.readInt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
