package dev.mapselect.network.role.juggernaut;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Syncs the viewer-independent, placement-axis-constrained direction of a broken door. */
public record JuggernautDoorDirectionPayload(BlockPos pos, int directionId) implements CustomPayload {
	public static final int CLEAR_DIRECTION = -1;
	public static final Id<JuggernautDoorDirectionPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "juggernaut_door_direction"));
	public static final PacketCodec<PacketByteBuf, JuggernautDoorDirectionPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeLong(payload.pos().asLong());
			buf.writeByte(payload.directionId());
		},
		buf -> new JuggernautDoorDirectionPayload(BlockPos.fromLong(buf.readLong()), buf.readByte())
	);

	public static JuggernautDoorDirectionPayload set(BlockPos pos, Direction direction) {
		return new JuggernautDoorDirectionPayload(pos.toImmutable(), direction.getId());
	}

	public static JuggernautDoorDirectionPayload clear(BlockPos pos) {
		return new JuggernautDoorDirectionPayload(pos.toImmutable(), CLEAR_DIRECTION);
	}

	public Direction direction() {
		if (directionId < 0) return null;
		Direction direction = Direction.byId(directionId);
		return direction.getAxis().isHorizontal() ? direction : null;
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
