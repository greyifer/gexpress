package dev.mapselect.replay;
/** Recorded tick spacing determines motion duration; packet batches must not shorten it. */
public final class ReplayMotionTiming {
    private ReplayMotionTiming(){}
    public static long interval(int recordedTicks,double speed){
        double validSpeed=Double.isFinite(speed)&&speed>0?speed:1;
        return (long)Math.clamp(Math.max(1,recordedTicks)*50_000_000.0/validSpeed,1_000_000.0,2_000_000_000.0);
    }
    public static double fraction(long now,long started,long interval){return Math.clamp((double)(now-started)/Math.max(1,interval),0,1);}
}
