package dev.mapselect.network.role.painter;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public record PainterDoorwayTransitionPayload(double offsetX, double offsetY, double offsetZ,
		int durationTicks) implements CustomPayload {
	public static final Id<PainterDoorwayTransitionPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "painter_doorway_transition"));
	private static final double MAX_OFFSET = 3.0D;

	public PainterDoorwayTransitionPayload {
		Vec3d clamped = clampOffset(new Vec3d(offsetX, offsetY, offsetZ));
		offsetX = clamped.x;
		offsetY = clamped.y;
		offsetZ = clamped.z;
		durationTicks = Math.max(1, Math.min(12, durationTicks));
	}

	public static final PacketCodec<PacketByteBuf, PainterDoorwayTransitionPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeDouble(payload.offsetX());
			buf.writeDouble(payload.offsetY());
			buf.writeDouble(payload.offsetZ());
			buf.writeVarInt(payload.durationTicks());
		},
		buf -> new PainterDoorwayTransitionPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(),
			buf.readVarInt())
	);

	public Vec3d offset() {
		return new Vec3d(offsetX, offsetY, offsetZ);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static Vec3d clampOffset(Vec3d offset) {
		if (offset == null || !Double.isFinite(offset.x) || !Double.isFinite(offset.y)
				|| !Double.isFinite(offset.z)) {
			return Vec3d.ZERO;
		}
		double lengthSquared = offset.lengthSquared();
		if (lengthSquared <= MAX_OFFSET * MAX_OFFSET) return offset;
		return offset.normalize().multiply(MAX_OFFSET);
	}
}
