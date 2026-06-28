package dev.mapselect.network.role.painter;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public record PainterDoorwayCrossPayload(BlockPos lowerPos, double playerX, double playerY, double playerZ,
		float yaw, float pitch) implements CustomPayload {
	public static final Id<PainterDoorwayCrossPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "painter_doorway_cross"));

	public PainterDoorwayCrossPayload {
		lowerPos = lowerPos == null ? BlockPos.ORIGIN : lowerPos.toImmutable();
	}

	public static final PacketCodec<PacketByteBuf, PainterDoorwayCrossPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBlockPos(payload.lowerPos());
			buf.writeDouble(payload.playerX());
			buf.writeDouble(payload.playerY());
			buf.writeDouble(payload.playerZ());
			buf.writeFloat(payload.yaw());
			buf.writeFloat(payload.pitch());
		},
		buf -> new PainterDoorwayCrossPayload(buf.readBlockPos(), buf.readDouble(), buf.readDouble(),
			buf.readDouble(), buf.readFloat(), buf.readFloat())
	);

	public Vec3d playerPos() {
		return new Vec3d(playerX, playerY, playerZ);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
