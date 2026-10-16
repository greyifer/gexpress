package dev.mapselect.role.veteran;

/** Thread-local bridge between Noelle's optional guess packet and cooldown component mixins. */
public final class NoellesGuessCancellation {
	private static final ThreadLocal<Boolean> BLOCKED_GUESS =
		ThreadLocal.withInitial(() -> false);

	private NoellesGuessCancellation() {}

	public static void markBlocked() {
		BLOCKED_GUESS.set(true);
	}

	public static boolean consumeBlocked() {
		boolean blocked = BLOCKED_GUESS.get();
		BLOCKED_GUESS.remove();
		return blocked;
	}
}
