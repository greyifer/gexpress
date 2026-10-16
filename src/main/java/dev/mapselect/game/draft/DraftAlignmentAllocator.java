package dev.mapselect.game.draft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Assigns team slots while keeping last round's Killer and Neutral players out of that same team. */
final class DraftAlignmentAllocator {
	private DraftAlignmentAllocator() {}

	enum Alignment {
		INNOCENT,
		NEUTRAL,
		KILLER
	}

	static <T> Allocation<T> allocate(List<T> orderedPlayers, int killerCount, int neutralCount,
			Function<T, Alignment> previousAlignment) {
		List<T> players = orderedPlayers == null
			? List.of() : new ArrayList<>(new LinkedHashSet<>(orderedPlayers));
		int killers = Math.max(0, killerCount);
		int neutrals = Math.max(0, neutralCount);
		if (killers + neutrals > players.size()) return Allocation.impossible();

		List<T> previousNeutrals = matching(players, previousAlignment, Alignment.NEUTRAL);
		List<T> unrestricted = matching(players, previousAlignment, Alignment.INNOCENT);
		List<T> previousKillers = matching(players, previousAlignment, Alignment.KILLER);
		if (killers > previousNeutrals.size() + unrestricted.size()
				|| neutrals > previousKillers.size() + unrestricted.size()) {
			return Allocation.impossible();
		}

		Set<T> killerPlayers = take(previousNeutrals, unrestricted, killers, Set.of());
		Set<T> neutralPlayers = take(previousKillers, unrestricted, neutrals, killerPlayers);
		if (killerPlayers.size() != killers || neutralPlayers.size() != neutrals) {
			return Allocation.impossible();
		}

		Map<T, Alignment> assignments = new LinkedHashMap<>();
		for (T player : players) {
			Alignment alignment = killerPlayers.contains(player) ? Alignment.KILLER
				: neutralPlayers.contains(player) ? Alignment.NEUTRAL : Alignment.INNOCENT;
			assignments.put(player, alignment);
		}
		return new Allocation<>(Map.copyOf(assignments), true);
	}

	private static <T> List<T> matching(List<T> players, Function<T, Alignment> previousAlignment,
			Alignment expected) {
		List<T> matches = new ArrayList<>();
		for (T player : players) {
			Alignment previous = previousAlignment == null ? null : previousAlignment.apply(player);
			if ((previous == null ? Alignment.INNOCENT : previous) == expected) matches.add(player);
		}
		return matches;
	}

	@SafeVarargs
	private static <T> Set<T> take(List<T> priority, List<T> fallback, int count, Set<T>... excludedSets) {
		Set<T> selected = new LinkedHashSet<>();
		addUntil(selected, priority, count, excludedSets);
		addUntil(selected, fallback, count, excludedSets);
		return selected;
	}

	@SafeVarargs
	private static <T> void addUntil(Set<T> selected, List<T> candidates, int count, Set<T>... excludedSets) {
		for (T candidate : candidates) {
			if (selected.size() >= count) return;
			boolean excluded = false;
			for (Set<T> excludedSet : excludedSets) {
				if (excludedSet != null && excludedSet.contains(candidate)) {
					excluded = true;
					break;
				}
			}
			if (!excluded) selected.add(candidate);
		}
	}

	record Allocation<T>(Map<T, Alignment> assignments, boolean possible) {
		private static <T> Allocation<T> impossible() {
			return new Allocation<>(Map.of(), false);
		}
	}
}
