package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SnitchProgressPayload(int completedTasks, int requiredTasks, boolean showProgress,
		List<InfoLine> infoLines) implements CustomPayload {
	public static final CustomPayload.Id<SnitchProgressPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "snitch_progress"));

	public static final PacketCodec<PacketByteBuf, SnitchProgressPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.completedTasks());
			buf.writeInt(payload.requiredTasks());
			buf.writeBoolean(payload.showProgress());
			buf.writeInt(payload.infoLines().size());
			for (InfoLine line : payload.infoLines()) {
				buf.writeUuid(line.playerId());
				buf.writeString(line.playerName());
				buf.writeString(line.roleName());
				buf.writeInt(line.roleColor());
			}
		},
		buf -> {
			int completed = buf.readInt();
			int required = buf.readInt();
			boolean showProgress = buf.readBoolean();
			int count = Math.max(0, Math.min(16, buf.readInt()));
			List<InfoLine> lines = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				lines.add(new InfoLine(
					buf.readUuid(),
					buf.readString(64),
					buf.readString(64),
					buf.readInt()
				));
			}
			return new SnitchProgressPayload(completed, required, showProgress, lines);
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record InfoLine(UUID playerId, String playerName, String roleName, int roleColor) {}
}
