package dev.mapselect.block;

import dev.doctor4t.wathe.block.property.OrnamentShape;
import net.minecraft.block.Block;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public final class FusedOrnamentGeometry {
	private static final VoxelShape LEDGE_NORTH_COLLISION = Block.createCuboidShape(0.0, 14.0, 0.0, 16.0, 16.0, 8.0);
	private static final VoxelShape LEDGE_EAST_COLLISION = Block.createCuboidShape(8.0, 14.0, 0.0, 16.0, 16.0, 16.0);
	private static final VoxelShape LEDGE_SOUTH_COLLISION = Block.createCuboidShape(0.0, 14.0, 8.0, 16.0, 16.0, 16.0);
	private static final VoxelShape LEDGE_WEST_COLLISION = Block.createCuboidShape(0.0, 14.0, 0.0, 8.0, 16.0, 16.0);

	private FusedOrnamentGeometry() {}

	public static VoxelShape goldLedgeCollisionShape(Direction facing, OrnamentShape shape) {
		if (facing == Direction.UP || facing == Direction.DOWN) {
			VoxelShape ledges = VoxelShapes.empty();
			if (hasLedgePart(shape, LedgePart.TOP)) ledges = VoxelShapes.union(ledges, LEDGE_NORTH_COLLISION);
			if (hasLedgePart(shape, LedgePart.RIGHT)) ledges = VoxelShapes.union(ledges, LEDGE_EAST_COLLISION);
			if (hasLedgePart(shape, LedgePart.BOTTOM)) ledges = VoxelShapes.union(ledges, LEDGE_SOUTH_COLLISION);
			if (hasLedgePart(shape, LedgePart.LEFT)) ledges = VoxelShapes.union(ledges, LEDGE_WEST_COLLISION);
			return ledges;
		}
		return switch (facing) {
			case EAST -> LEDGE_EAST_COLLISION;
			case SOUTH -> LEDGE_SOUTH_COLLISION;
			case WEST -> LEDGE_WEST_COLLISION;
			default -> LEDGE_NORTH_COLLISION;
		};
	}

	public static boolean hasLedgePart(OrnamentShape shape, LedgePart part) {
		return switch (part) {
			case LEFT -> switch (shape) {
				case LEFT, LEFT_RIGHT, LEFT_RIGHT_CENTER, LEFT_TOP, LEFT_BOTTOM, LEFT_RIGHT_TOP,
					LEFT_RIGHT_BOTTOM, LEFT_TOP_BOTTOM, ALL -> true;
				default -> false;
			};
			case RIGHT -> switch (shape) {
				case RIGHT, LEFT_RIGHT, LEFT_RIGHT_CENTER, RIGHT_TOP, RIGHT_BOTTOM, LEFT_RIGHT_TOP,
					LEFT_RIGHT_BOTTOM, RIGHT_TOP_BOTTOM, ALL -> true;
				default -> false;
			};
			case TOP -> switch (shape) {
				case TOP, LEFT_TOP, RIGHT_TOP, TOP_BOTTOM, LEFT_RIGHT_TOP, LEFT_TOP_BOTTOM,
					RIGHT_TOP_BOTTOM, ALL -> true;
				default -> false;
			};
			case BOTTOM -> switch (shape) {
				case BOTTOM, LEFT_BOTTOM, RIGHT_BOTTOM, TOP_BOTTOM, LEFT_RIGHT_BOTTOM, LEFT_TOP_BOTTOM,
					RIGHT_TOP_BOTTOM, ALL -> true;
				default -> false;
			};
		};
	}

	public enum LedgePart {
		LEFT,
		RIGHT,
		TOP,
		BOTTOM
	}
}
