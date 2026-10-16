package dev.mapselect.network.role.spy;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SpyListenPayload(boolean listening) implements CustomPayload {
	public static final Id<SpyListenPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "spy_listen"));

	public static final PacketCodec<PacketByteBuf, SpyListenPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeBoolean(payload.listening()),
		buf -> new SpyListenPayload(buf.readBoolean())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
