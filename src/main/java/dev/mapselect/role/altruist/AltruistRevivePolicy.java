package dev.mapselect.role.altruist;

public final class AltruistRevivePolicy {
	public static final int MAX_REVIVE_AGE_TICKS = 60 * 20;

	private AltruistRevivePolicy() {}

	public static boolean canReviveBodyAge(int bodyAgeTicks) {
		return bodyAgeTicks >= 0 && bodyAgeTicks < MAX_REVIVE_AGE_TICKS;
	}
}
