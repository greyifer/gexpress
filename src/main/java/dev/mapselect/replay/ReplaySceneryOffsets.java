package dev.mapselect.replay;

import java.nio.ByteBuffer;

/** Wathe's Sodium offsets evaluated in recorded coordinates, before stage translation. */
public final class ReplaySceneryOffsets {
    private ReplaySceneryOffsets() {}
    public static void write(ByteBuffer buffer, int section, int x, int y, int z, int cameraZ,
                             double fracX, double fracY, double fracZ, float time, float speed) {
        float vx = (float)(x - fracX), vy = (float)(y - fracY), vz = (float)(z - fracZ);
        float fx = vx, fy = vy, fz = vz;
        int relativeZ = z / 16 - Math.floorDiv(cameraZ, 16);
        float travel = time / 73.8F * speed;
        if (relativeZ <= -8) {
            fx = (vx - 512 + travel) % 1536 - 768; fy += 116; fz += 240;
        } else if (relativeZ >= 8) {
            fx = (vx + 512 + travel) % 1536 - 768; fy += 116; fz -= 240;
        } else if (Math.floorDiv(y, 16) < 4) {
            fx = (vx + travel) % 1536 - 768; fy += 116;
        }
        buffer.putFloat(section * 16, (float)(fx - x + fracX));
        buffer.putFloat(section * 16 + 4, (float)(fy - y + fracY));
        buffer.putFloat(section * 16 + 8, (float)(fz - z + fracZ));
    }
}
