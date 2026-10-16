package dev.mapselect.replay;
/** Server-side limb animation values are often zero; recorded travel is authoritative. */
public final class ReplayWalkAnimation {
    private ReplayWalkAnimation() {}
    public static float distance(double dx, double dz, boolean jump) {
        return jump ? 0 : (float)Math.hypot(dx, dz);
    }
    public static float speed(float distance, int ticks) {
        return Math.min(1, distance * 4 / Math.max(1, ticks));
    }
}
