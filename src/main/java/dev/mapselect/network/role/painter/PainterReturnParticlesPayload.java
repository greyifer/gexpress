package dev.mapselect.network.role.painter;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PainterReturnParticlesPayload(double x, double y, double z, int color) implements CustomPayload {
	public static final Id<PainterReturnParticlesPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "painter_return_particles"));

	public PainterReturnParticlesPayload {
		color &= 0xFFFFFF;
	}

	public static final PacketCodec<PacketByteBuf, PainterReturnParticlesPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeDouble(payload.x());
			buf.writeDouble(payload.y());
			buf.writeDouble(payload.z());
			buf.writeInt(payload.color());
		},
		buf -> new PainterReturnParticlesPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
