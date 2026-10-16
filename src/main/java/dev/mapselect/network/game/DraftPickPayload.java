package dev.mapselect.network.game;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DraftPickPayload(int turn, int choice, Identifier roleId) implements CustomPayload {
	public static final Id<DraftPickPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "draft_pick"));

	public static final PacketCodec<PacketByteBuf, DraftPickPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(payload.turn());
			buf.writeVarInt(payload.choice());
			buf.writeBoolean(payload.roleId() != null);
			if (payload.roleId() != null) buf.writeIdentifier(payload.roleId());
		},
		buf -> new DraftPickPayload(buf.readVarInt(), buf.readVarInt(),
			buf.readBoolean() ? buf.readIdentifier() : null)
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
