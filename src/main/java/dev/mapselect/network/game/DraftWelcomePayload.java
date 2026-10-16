package dev.mapselect.network.game;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DraftWelcomePayload(Identifier roleId, int color, int killers, int targets)
		implements CustomPayload {
	public static final Id<DraftWelcomePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "draft_welcome"));

	public DraftWelcomePayload {
		if (roleId == null) roleId = Identifier.of("wathe", "civilian");
		color &= 0xFFFFFF;
		killers = Math.max(0, killers);
		targets = Math.max(0, targets);
	}

	public static final PacketCodec<PacketByteBuf, DraftWelcomePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeIdentifier(payload.roleId());
			buf.writeInt(payload.color());
			buf.writeVarInt(payload.killers());
			buf.writeVarInt(payload.targets());
		},
		buf -> new DraftWelcomePayload(buf.readIdentifier(), buf.readInt(),
			buf.readVarInt(), buf.readVarInt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
