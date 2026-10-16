package dev.mapselect.block;

import dev.mapselect.registry.MapSelectBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.function.Consumer;

public record SettingsPanel(BlockPos origin, int width, int height, Direction facing) {
	public static final int MAX_WIDTH = 16;
	public static final int MAX_HEIGHT = 16;

	public SettingsPanel {
		origin = origin == null ? BlockPos.ORIGIN : origin.toImmutable();
		width = Math.max(1, Math.min(MAX_WIDTH, width));
		height = Math.max(1, Math.min(MAX_HEIGHT, height));
		facing = facing == null || facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
	}

	public static SettingsPanel find(World world, BlockPos pos) {
		if (world == null || pos == null) return null;
		BlockState state = world.getBlockState(pos);
		if (!state.isOf(MapSelectBlocks.SETTINGS_PANEL)) return null;
		Direction facing = state.get(SettingsPanelBlock.FACING);
		Direction right = facing.rotateYClockwise();
		BlockPos rowStart = pos.toImmutable();
		for (int i = 1; i < MAX_WIDTH; i++) {
			BlockPos next = rowStart.offset(right.getOpposite());
			if (!matches(world, next, facing)) break;
			rowStart = next;
		}
		int width = 1;
		while (width < MAX_WIDTH && matches(world, rowStart.offset(right, width), facing)) width++;

		BlockPos origin = rowStart;
		for (int i = 1; i < MAX_HEIGHT; i++) {
			BlockPos below = origin.down();
			if (!completeRow(world, below, right, width, facing)) break;
			origin = below;
		}
		int height = 1;
		while (height < MAX_HEIGHT && completeRow(world, origin.up(height), right, width, facing)) height++;
		return new SettingsPanel(origin, width, height, facing);
	}

	public Direction right() {
		return facing.rotateYClockwise();
	}

	public boolean isOrigin(BlockPos pos) {
		return origin.equals(pos);
	}

	public SettingsPanelBlockEntity controller(World world) {
		if (world != null && world.getBlockEntity(origin) instanceof SettingsPanelBlockEntity settings) {
			return settings;
		}
		return null;
	}

	public void forEach(Consumer<BlockPos> action) {
		if (action == null) return;
		Direction right = right();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				action.accept(origin.offset(right, x).up(y));
			}
		}
	}

	public double horizontalCoordinate(net.minecraft.util.math.Vec3d hitPos) {
		double x = hitPos.x - origin.getX();
		double z = hitPos.z - origin.getZ();
		Direction right = right();
		if (right.getOffsetX() > 0) return x;
		if (right.getOffsetX() < 0) return 1.0D - x;
		if (right.getOffsetZ() > 0) return z;
		return 1.0D - z;
	}

	/** Horizontal position as seen by a player looking at the front of the panel. */
	public double screenHorizontalCoordinate(net.minecraft.util.math.Vec3d hitPos) {
		return width - horizontalCoordinate(hitPos);
	}

	public double verticalCoordinate(net.minecraft.util.math.Vec3d hitPos) {
		return hitPos.y - origin.getY();
	}

	private static boolean completeRow(World world, BlockPos start, Direction right, int width, Direction facing) {
		for (int x = 0; x < width; x++) {
			if (!matches(world, start.offset(right, x), facing)) return false;
		}
		return true;
	}

	private static boolean matches(World world, BlockPos pos, Direction facing) {
		BlockState state = world.getBlockState(pos);
		return state.isOf(MapSelectBlocks.SETTINGS_PANEL)
			&& state.get(SettingsPanelBlock.FACING) == facing;
	}
}
