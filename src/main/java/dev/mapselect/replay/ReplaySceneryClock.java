package dev.mapselect.replay;

/** Interpolates network snapshots but freezes one value for all sections of a render frame. */
public final class ReplaySceneryClock {
    private double from, to;
    private long started, interval = 50_000_000;
    private float frameTime;
    public void update(int target, long now, long interval, boolean snap) {
        from = snap ? target : sample(now, false); to = target;
        started = now; this.interval = Math.max(1, interval);
        if (snap) frameTime = target;
    }
    private float sample(long now, boolean hold) {
        double fraction = hold ? 1 : Math.clamp((double)(now - started) / interval, 0, 1);
        return (float)(from + (to - from) * fraction);
    }
    public void beginFrame(long now, boolean hold) { frameTime = sample(now, hold); }
    public float time() { return frameTime; }
    public void reset() { from = to = frameTime = 0; started = 0; interval = 50_000_000; }
}
