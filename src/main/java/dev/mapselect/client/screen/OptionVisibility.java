package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.Option;
import dev.mapselect.mixin.client.YaclOptionNameAccessor;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class OptionVisibility {
	private OptionVisibility() {}

	private static final Map<Option<?>, BooleanSupplier> HIDDEN_PREDICATES = new IdentityHashMap<>();

	public static void setHiddenWhen(Option<?> option, BooleanSupplier hiddenIf) {
		HIDDEN_PREDICATES.put(option, hiddenIf);
	}

	public static boolean isHidden(Option<?> option) {
		BooleanSupplier s = HIDDEN_PREDICATES.get(option);
		return s != null && s.getAsBoolean();
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
	}
}
