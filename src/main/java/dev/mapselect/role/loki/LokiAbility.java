package dev.mapselect.role.loki;

public enum LokiAbility {
    VOICES(120,45), HEX(30,30), FUSE(30,60), CLONE(180,90);
    public final int durationTicks, cooldownTicks;
    public long activationTick(long usedAt){return usedAt+((this==HEX||this==FUSE)?300:0);}
    public long expiryTick(long usedAt){return activationTick(usedAt)+durationTicks;}
    LokiAbility(int durationSeconds,int cooldownSeconds) { durationTicks=durationSeconds*20; cooldownTicks=cooldownSeconds*20; }
    public static LokiAbility fromIndex(int index) { return index<0 || index>=values().length ? null : values()[index]; }
}
