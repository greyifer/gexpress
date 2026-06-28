package dev.mapselect.network.tutorial;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TutorialControlPayload(int action) implements CustomPayload {
	public static final int START = 1;
	public static final int EXIT = 2;
	public static final int EDIT_ROOM = 3;
	public static final int SAVE_ROOM = 4;
	public static final CustomPayload.Id<TutorialControlPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "tutorial_control"));
	public static final PacketCodec<PacketByteBuf, TutorialControlPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.VAR_INT, TutorialControlPayload::action,
		TutorialControlPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
