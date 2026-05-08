package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Optional task settings added outside the legacy main config packet shape. */
public record GexpressTaskConfigPayload(boolean conversationEnabled, int conversationChancePercent,
		int conversationDurationSeconds, int conversationRadiusBlocks,
		int conversationVerticalToleranceBlocks) implements CustomPayload {
	public static final CustomPayload.Id<GexpressTaskConfigPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "task_config"));

	public static final PacketCodec<PacketByteBuf, GexpressTaskConfigPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.conversationEnabled());
			buf.writeInt(payload.conversationChancePercent());
			buf.writeInt(payload.conversationDurationSeconds());
			buf.writeInt(payload.conversationRadiusBlocks());
			buf.writeInt(payload.conversationVerticalToleranceBlocks());
		},
		buf -> new GexpressTaskConfigPayload(
			buf.readBoolean(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt()
		)
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
