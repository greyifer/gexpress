package dev.mapselect.game.draft;

/** Team-slot totals used to build the ordinary three-role offers for one draft. */
public record DraftSlotBudget(int killers, int neutrals, int innocents) {
	public static DraftSlotBudget calculate(int playerCount, boolean hasKillers, boolean hasNeutrals,
			boolean useCustomCounts, int playersPerKiller, int maxKillers,
			int playersPerNeutral, int maxNeutrals) {
		playerCount = Math.max(0, playerCount);
		int killers = hasKillers && playerCount > 1
			? Math.max(1, playerCount / Math.max(1, playersPerKiller)) : 0;
		if (useCustomCounts) killers = Math.min(killers, Math.max(0, maxKillers));
		killers = Math.min(Math.max(0, playerCount - 1), killers);

		int neutrals = hasNeutrals ? playerCount / Math.max(1, playersPerNeutral) : 0;
		if (useCustomCounts) neutrals = Math.min(neutrals, Math.max(0, maxNeutrals));
		neutrals = Math.min(Math.max(0, playerCount - killers - 1), neutrals);

		return new DraftSlotBudget(killers, neutrals, playerCount - killers - neutrals);
	}
}
