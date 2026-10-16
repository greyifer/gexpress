package dev.mapselect.replay;

import io.github.caspe.glib.api.room.RoomDimensions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Archive bounds describe space; empty space is not a runtime room allocation. */
record ReplayCaptureBounds(BlockPos min, BlockPos max, RoomDimensions dimensions) {
    static Box sceneryArea(Box playArea, int bottomY) {
        return new Box(Math.floor(playArea.minX / 16) * 16 - 768, bottomY, Math.floor(playArea.minZ / 16) * 16 - 240,
            Math.ceil(playArea.maxX / 16) * 16 + 768, Math.max(64, playArea.maxY), Math.ceil(playArea.maxZ / 16) * 16 + 240);
    }
    static ReplayCaptureBounds of(Box area, int bottomY, int topY) {
        if (area == null || !Double.isFinite(area.minX) || !Double.isFinite(area.maxX)
                || !Double.isFinite(area.minY) || !Double.isFinite(area.maxY)
                || !Double.isFinite(area.minZ) || !Double.isFinite(area.maxZ)) throw new IllegalArgumentException("Replay play area is missing or invalid");
        BlockPos min = BlockPos.ofFloored(area.minX, Math.max(area.minY, bottomY), area.minZ);
        BlockPos max = BlockPos.ofFloored(Math.ceil(area.maxX) - 1, Math.min(Math.ceil(area.maxY) - 1, topY - 1), Math.ceil(area.maxZ) - 1);
        RoomDimensions dimensions = new RoomDimensions(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
        if (dimensions.height() > 1024) throw new IllegalArgumentException("Replay capture height exceeds 1024 blocks");
        return new ReplayCaptureBounds(min, max, dimensions);
    }
    Box box() { return new Box(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1); }
}
