package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.Option;
import dev.mapselect.mixin.client.YaclOptionNameAccessor;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.Locale;

public final class OptionVisibility {
	private OptionVisibility() {}

	private static final Map<Option<?>, BooleanSupplier> HIDDEN_PREDICATES = new IdentityHashMap<>();
	private static final Map<Option<?>, String> SEARCH_ALIASES = new IdentityHashMap<>();
	private static final Map<Option<?>, Boolean> SEARCH_ALIAS_MATCHES = new IdentityHashMap<>();

	public static void setHiddenWhen(Option<?> option, BooleanSupplier hiddenIf) {
		HIDDEN_PREDICATES.put(option, hiddenIf);
	}

	public static void addSearchAlias(Option<?> option, String alias) {
		if (option == null || alias == null || alias.isBlank()) return;
		String normalized = alias.toLowerCase(Locale.ROOT);
		String existing = SEARCH_ALIASES.get(option);
		SEARCH_ALIASES.put(option, existing == null ? normalized : existing + " " + normalized);
	}

	public static boolean isHidden(Option<?> option) {
		BooleanSupplier s = HIDDEN_PREDICATES.get(option);
		return s != null && s.getAsBoolean();
	}

	public static boolean matchesSearchAlias(Option<?> option, String query) {
		if (option == null || query == null || query.isBlank()) return false;
		String alias = SEARCH_ALIASES.get(option);
		return alias != null && alias.contains(query.toLowerCase(Locale.ROOT));
	}

	public static boolean updateSearchAliasMatch(Option<?> option, String query) {
		boolean matches = matchesSearchAlias(option, query);
		if (option != null) {
			SEARCH_ALIAS_MATCHES.put(option, matches);
		}
		return matches;
	}

	public static boolean isSearchAliasMatched(Option<?> option) {
		return Boolean.TRUE.equals(SEARCH_ALIAS_MATCHES.get(option));
	}

	public static MutableText dropdownName(Text name) {
		String raw = name.getString().stripLeading();
		if (raw.startsWith("\u2514")) return name.copy();
		return Text.literal("  ")
			.append(Text.literal("\u2514 ").formatted(Formatting.DARK_GRAY))
			.append(name.copy());
	}

	public static void ensureDropdownName(Option<?> option) {
		String raw = option.name().getString().stripLeading();
		if (raw.startsWith("\u2514")) return;
		if (option instanceof YaclOptionNameAccessor accessor) {
			accessor.gexpress$setName(dropdownName(option.name()));
		}
	}

	public static void clearAll() {
		HIDDEN_PREDICATES.clear();
		SEARCH_ALIASES.clear();
		SEARCH_ALIAS_MATCHES.clear();
	}
}
