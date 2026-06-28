package dev.mapselect.network.tutorial;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TutorialStatePayload(boolean active, int stage, String instruction, int fadeTicks,
		boolean completed) implements CustomPayload {
	public static final CustomPayload.Id<TutorialStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "tutorial_state"));

	public TutorialStatePayload {
		stage = Math.max(0, stage);
		instruction = instruction == null ? "" : instruction;
		fadeTicks = Math.max(0, fadeTicks);
	}

	public static TutorialStatePayload clear(boolean completed) {
		return new TutorialStatePayload(false, 0, "", 0, completed);
	}

	public static final PacketCodec<PacketByteBuf, TutorialStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeVarInt(payload.stage());
			buf.writeString(payload.instruction(), 256);
			buf.writeVarInt(payload.fadeTicks());
			buf.writeBoolean(payload.completed());
		},
		buf -> new TutorialStatePayload(buf.readBoolean(), buf.readVarInt(), buf.readString(256),
			buf.readVarInt(), buf.readBoolean())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
