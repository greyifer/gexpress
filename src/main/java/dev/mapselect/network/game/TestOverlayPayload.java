package dev.mapselect.network.game;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TestOverlayPayload(String overlay, int durationTicks) implements CustomPayload {
	public static final Id<TestOverlayPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "test_overlay"));

	public TestOverlayPayload {
		overlay = overlay == null ? "clear" : overlay;
		durationTicks = Math.max(0, durationTicks);
	}

	public static final PacketCodec<PacketByteBuf, TestOverlayPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeString(payload.overlay(), 32);
			buf.writeInt(payload.durationTicks());
		},
		buf -> new TestOverlayPayload(buf.readString(32), buf.readInt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
