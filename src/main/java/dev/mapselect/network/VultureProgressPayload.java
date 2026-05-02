package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client Pelican win-progress counter. */
public record VultureProgressPayload(boolean show, int eaten, int required) implements CustomPayload {
	public static final CustomPayload.Id<VultureProgressPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "vulture_progress"));

	public static final PacketCodec<PacketByteBuf, VultureProgressPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.show());
			buf.writeInt(payload.eaten());
			buf.writeInt(payload.required());
		},
		buf -> new VultureProgressPayload(buf.readBoolean(), buf.readInt(), buf.readInt())
	);

	public static VultureProgressPayload clear() {
		return new VultureProgressPayload(false, 0, 1);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
