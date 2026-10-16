package dev.mapselect.game.draft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import java.util.function.BooleanSupplier;

final class DraftOfferPlanner {
	private DraftOfferPlanner() {}

	static <T> boolean canOfferAlignment(T current, T candidate, Iterable<T> futureReserved) {
		if (Objects.equals(current, candidate)) return true;
		if (futureReserved == null) return false;
		for (T future : futureReserved) {
			if (Objects.equals(candidate, future)) return true;
		}
		return false;
	}

	static <T> boolean canApplyAlignment(T reserved, T selected, BooleanSupplier swapReservation) {
		if (Objects.equals(reserved, selected)) return true;
		return swapReservation != null && swapReservation.getAsBoolean();
	}

	static <T> List<T> select(List<T> candidates, int count, Function<T, String> key,
			ToIntFunction<T> weight, IntUnaryOperator nextInt) {
		if (candidates == null || candidates.isEmpty() || count <= 0) return List.of();

		Map<String, T> distinct = new LinkedHashMap<>();
		for (T candidate : candidates) {
			if (candidate == null) continue;
			String candidateKey = key.apply(candidate);
			if (candidateKey == null || candidateKey.isBlank()) continue;
			distinct.putIfAbsent(candidateKey, candidate);
		}
		if (distinct.isEmpty()) return List.of();

		List<T> uniquePool = new ArrayList<>(distinct.values());
		List<T> choices = new ArrayList<>(count);
		while (!uniquePool.isEmpty() && choices.size() < count) {
			T selected = removeWeighted(uniquePool, weight, nextInt);
			if (selected == null) break;
			choices.add(selected);
		}

		List<T> repeatPool = List.copyOf(distinct.values());
		while (choices.size() < count) {
			T selected = weighted(repeatPool, weight, nextInt);
			if (selected == null) break;
			choices.add(selected);
		}
		return List.copyOf(choices);
	}

	private static <T> T removeWeighted(List<T> values, ToIntFunction<T> weight,
			IntUnaryOperator nextInt) {
		T selected = weighted(values, weight, nextInt);
		if (selected != null) values.remove(selected);
		return selected;
	}

	private static <T> T weighted(List<T> values, ToIntFunction<T> weight,
			IntUnaryOperator nextInt) {
		if (values == null || values.isEmpty()) return null;
		int totalWeight = 0;
		for (T value : values) totalWeight += Math.max(1, weight.applyAsInt(value));
		int roll = Math.floorMod(nextInt.applyAsInt(totalWeight), totalWeight);
		for (T value : values) {
			roll -= Math.max(1, weight.applyAsInt(value));
			if (roll < 0) return value;
		}
		return values.getLast();
	}
}
