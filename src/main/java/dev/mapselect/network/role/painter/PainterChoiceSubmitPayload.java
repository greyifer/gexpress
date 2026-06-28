package dev.mapselect.network.role.painter;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record PainterChoiceSubmitPayload(PainterChoiceOpenPayload.Mode mode, UUID targetId,
		Identifier deathReasonId, Identifier roleId) implements CustomPayload {
	public static final Id<PainterChoiceSubmitPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "painter_choice_submit"));

	public PainterChoiceSubmitPayload {
		mode = mode == null ? PainterChoiceOpenPayload.Mode.PLAYER : mode;
	}

	public static final PacketCodec<PacketByteBuf, PainterChoiceSubmitPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeEnumConstant(payload.mode());
			buf.writeUuid(payload.targetId());
			buf.writeBoolean(payload.deathReasonId() != null);
			if (payload.deathReasonId() != null) buf.writeIdentifier(payload.deathReasonId());
			buf.writeBoolean(payload.roleId() != null);
			if (payload.roleId() != null) buf.writeIdentifier(payload.roleId());
		},
		buf -> new PainterChoiceSubmitPayload(buf.readEnumConstant(PainterChoiceOpenPayload.Mode.class),
			buf.readUuid(), buf.readBoolean() ? buf.readIdentifier() : null,
			buf.readBoolean() ? buf.readIdentifier() : null)
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
