package dev.mapselect.network.role.mafia;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Server-confirmed Godfather target used by the role-selection inventory. */
public record MafiaRecruitOpenPayload(UUID targetId, String targetName) implements CustomPayload {
	public static final Id<MafiaRecruitOpenPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "mafia_recruit_open"));

	public static final PacketCodec<PacketByteBuf, MafiaRecruitOpenPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeUuid(payload.targetId());
			buf.writeString(payload.targetName(), 64);
		},
		buf -> new MafiaRecruitOpenPayload(buf.readUuid(), buf.readString(64))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
