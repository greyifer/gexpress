package dev.mapselect.role.juggernaut;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Selects one of the two valid fall animations on the door's placed axis. */
final class JuggernautDoorFallDirection {
	private static final double LOOK_EPSILON = 1.0E-6D;

	private JuggernautDoorFallDirection() {}

	static Direction resolve(Direction doorFacing, Vec3d lookDirection) {
		if (doorFacing == null || !doorFacing.getAxis().isHorizontal()) return Direction.NORTH;
		if (lookDirection == null) return doorFacing;
		if (doorFacing.getAxis() == Direction.Axis.X) {
			if (Math.abs(lookDirection.x) <= LOOK_EPSILON) return doorFacing;
			return lookDirection.x > 0.0D ? Direction.EAST : Direction.WEST;
		}
		if (Math.abs(lookDirection.z) <= LOOK_EPSILON) return doorFacing;
		return lookDirection.z > 0.0D ? Direction.SOUTH : Direction.NORTH;
	}
}
