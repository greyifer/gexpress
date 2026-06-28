package dev.mapselect.network.progression;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SkinCaseResultPayload(String caseId, String typeId, String skinId,
		boolean newUnlock, int duplicateRefund) implements CustomPayload {
	public static final CustomPayload.Id<SkinCaseResultPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "skin_case_result"));

	public SkinCaseResultPayload {
		caseId = caseId == null ? "" : caseId;
		typeId = typeId == null ? "" : typeId;
		skinId = skinId == null ? "" : skinId;
		duplicateRefund = Math.max(0, duplicateRefund);
	}

	public static final PacketCodec<PacketByteBuf, SkinCaseResultPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeString(payload.caseId());
			buf.writeString(payload.typeId());
			buf.writeString(payload.skinId());
			buf.writeBoolean(payload.newUnlock());
			buf.writeInt(payload.duplicateRefund());
		},
		buf -> new SkinCaseResultPayload(
			buf.readString(),
			buf.readString(),
			buf.readString(),
			buf.readBoolean(),
			buf.readInt()
		)
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
