package dev.mapselect.replay;

/** Replay time is measured in server ticks; playback never changes the live server clock. */
public final class ReplayClock {
    public static final double[] SPEEDS = {0.25, 0.5, 1, 2, 4, 8};
    private double position;
    private double speed = 1;
    private boolean paused;
    private final int duration;

    public ReplayClock(int duration) { this.duration = Math.max(0, duration); }
    public int tick() { return (int) position; }
    public int duration() { return duration; }
    public double speed() { return speed; }
    public boolean paused() { return paused; }
    public void pause(boolean value) { paused = value; }
    public void seek(int tick) { position = Math.clamp(tick, 0, duration); }
    public void speed(double value) {
        for (double allowed : SPEEDS) if (value == allowed) { speed = value; return; }
    }
    public int nextTick() { return paused ? tick() : (int)Math.min(duration, position + speed); }
    public void advance() {
        if (!paused) position = Math.min(duration, position + speed);
        if (position >= duration) paused = true;
    }
}
