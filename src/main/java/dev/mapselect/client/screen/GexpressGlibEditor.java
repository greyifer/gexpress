package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.ListOptionEntry;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.controllers.BooleanController;
import dev.isxander.yacl3.gui.controllers.cycling.ICyclingController;
import dev.isxander.yacl3.gui.controllers.slider.ISliderController;
import dev.isxander.yacl3.gui.controllers.string.IStringController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Adapts G'Express setting bindings to G'Lib-owned workspaces and controls. */
final class GexpressGlibEditor {
	record PageSource(Text title, Text subtitle, List<OptionGroup> groups) {
		PageSource {
			groups = groups == null ? List.of() : List.copyOf(groups);
		}
	}

	private GexpressGlibEditor() {
	}

	static Screen screen(Screen parent, String title, String subtitle, int accent, ConfigCategory category) {
		List<OptionGroup> groups = category.groups().stream().filter(GexpressGlibEditor::hasContent).toList();
		return screen(parent, title, subtitle, accent,
			List.of(new PageSource(category.name(), category.tooltip(), groups)));
	}

	static Screen screen(Screen parent, String title, String subtitle, int accent, List<PageSource> sources) {
		Set<Option<?>> trackedOptions = collectOptions(sources);
		GexpressSettingsEditorScreen.Builder builder = GexpressSettingsEditorScreen.builder(parent, Text.literal(title))
			.subtitle(Text.literal(subtitle))
			.accentColor(accent)
			.liveChanges()
			.onSave(() -> {
				for (Option<?> option : trackedOptions) option.applyValue();
				GexpressOptionsScreen.finishGlibSave();
			});
		for (PageSource source : sources) {
			builder.page(toPage(source, accent));
		}
		return builder.build();
	}

	private static GexpressSettingsEditorScreen.Page toPage(PageSource source, int accent) {
		List<GexpressSettingsEditorScreen.Section> sections = new ArrayList<>();
		for (OptionGroup group : source.groups()) {
			if (!hasContent(group)) continue;
			if (group instanceof ListOption<?> list) {
				sections.add(listSection(list, accent));
				continue;
			}
			List<GexpressSettingsEditorScreen.Entry> entries = new ArrayList<>();
			for (Option<?> option : group.options()) {
				if (!OptionVisibility.isHidden(option) || OptionVisibility.hasSearchAlias(option)) {
					entries.add(toEntry(option, accent));
				}
			}
			Text sectionTitle = group.name().getString().isBlank() ? source.title() : group.name();
			sections.add(new GexpressSettingsEditorScreen.Section(sectionTitle, groupDescription(group), entries));
		}
		if (sections.isEmpty()) {
			sections.add(new GexpressSettingsEditorScreen.Section(source.title(), source.subtitle(), List.of(
				new GexpressSettingsEditorScreen.LabelEntry(Text.literal("No settings available"), Text.empty(), () -> false)
			)));
		}
		return new GexpressSettingsEditorScreen.Page(source.title(), source.subtitle(), sections);
	}

	private static GexpressSettingsEditorScreen.Section listSection(ListOption<?> list, int accent) {
		List<GexpressSettingsEditorScreen.Entry> entries = List.of(
			new GexpressSettingsEditorScreen.LabelEntry(
				Text.literal(list.numberOfEntries() + " configured entries"), groupDescription(list), list::available),
			new GexpressSettingsEditorScreen.ActionEntry(list.name(), groupDescription(list), list::available,
				Text.literal("Edit List"), () -> openListEditor(list, accent))
		);
		return new GexpressSettingsEditorScreen.Section(list.name(), groupDescription(list), entries);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void openListEditor(ListOption<?> rawList, int accent) {
		openListEditor(rawList, accent, MinecraftClient.getInstance().currentScreen);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void openListEditor(ListOption<?> rawList, int accent, Screen parent) {
		ListOption list = rawList;
		List<GexpressSettingsEditorScreen.Entry> entries = new ArrayList<>();
		for (int index = 0; index < list.options().size(); index++) {
			ListOptionEntry entry = (ListOptionEntry) list.options().get(index);
			int entryIndex = index;
			entries.add(toEntry(entry, accent));
			entries.add(new GexpressSettingsEditorScreen.ActionEntry(Text.literal("Remove entry " + (entryIndex + 1)), Text.empty(),
				() -> list.numberOfEntries() > list.minimumNumberOfEntries(), Text.literal("Remove"), () -> {
					list.removeEntry(entry);
					GexpressOptionsScreen.requestGlibAutosave(list);
					openListEditor(list, accent, parent);
				}));
		}
		entries.add(new GexpressSettingsEditorScreen.ActionEntry(Text.literal("Add entry"), Text.empty(),
			() -> list.numberOfEntries() < list.maximumNumberOfEntries(), Text.literal("Add"), () -> {
				list.insertNewEntry();
				GexpressOptionsScreen.requestGlibAutosave(list);
				openListEditor(list, accent, parent);
			}));

		GexpressSettingsEditorScreen screen = GexpressSettingsEditorScreen.builder(parent, list.name())
			.subtitle(groupDescription(list))
			.accentColor(accent)
			.liveChanges()
			.onSave(() -> {
				list.applyValue();
				GexpressOptionsScreen.finishGlibSave();
			})
			.page(new GexpressSettingsEditorScreen.Page(list.name(), groupDescription(list), List.of(
				new GexpressSettingsEditorScreen.Section(Text.literal("Entries"), Text.empty(), entries))))
			.build();
		MinecraftClient.getInstance().setScreen(screen);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static GexpressSettingsEditorScreen.Entry toEntry(Option<?> rawOption, int accent) {
		if (rawOption instanceof LabelOption label) {
			return new GexpressSettingsEditorScreen.LabelEntry(label.label(), optionDescription(label), () -> true);
		}
		if (rawOption instanceof ButtonOption button) {
			return new GexpressSettingsEditorScreen.ActionEntry(button.name(), optionDescription(button), button::available,
				button.controller().formatValue(), () -> {
					button.action().accept(null, button);
					GexpressOptionsScreen.requestGlibAutosave(button);
				});
		}

		Option option = rawOption;
		Object controller = option.controller();
		if (controller instanceof BooleanController booleanController) {
			return new GexpressSettingsEditorScreen.ToggleEntry(option.name(), optionDescription(option), option::available,
				() -> (Boolean) option.pendingValue(), value -> {
					option.requestSet(value);
					GexpressOptionsScreen.requestGlibAutosave(option);
				},
				value -> booleanController.formatValue());
		}
		if (controller instanceof ICyclingController cycling) {
			return new GexpressSettingsEditorScreen.CycleEntry(option.name(), optionDescription(option), option::available,
				cycling::formatValue, () -> {
					int length = Math.max(1, cycling.getCycleLength());
					cycling.setPendingValue((cycling.getPendingValue() + 1) % length);
					GexpressOptionsScreen.requestGlibAutosave(option);
				});
		}
		if (controller instanceof ISliderController slider) {
			return new GexpressSettingsEditorScreen.SliderEntry(option.name(), optionDescription(option), option::available,
				slider::pendingValue,
				value -> {
					slider.setPendingValue(value);
					GexpressOptionsScreen.requestGlibAutosave(option);
				},
				value -> slider.formatValue(), slider.min(), slider.max());
		}
		if (controller instanceof IStringController stringController) {
			return new GexpressSettingsEditorScreen.TextEntry(option.name(), optionDescription(option), option::available,
				stringController::getString, value -> {
					stringController.setFromString(value);
					GexpressOptionsScreen.requestGlibAutosave(option);
				},
				stringController::isInputValid, 4096);
		}

		return new GexpressSettingsEditorScreen.LabelEntry(option.name(), option.controller().formatValue(), option::available);
	}

	private static Set<Option<?>> collectOptions(List<PageSource> sources) {
		Set<Option<?>> options = new LinkedHashSet<>();
		for (PageSource source : sources) {
			for (OptionGroup group : source.groups()) {
				if (group instanceof ListOption<?> list) {
					options.add(list);
				} else {
					options.addAll(group.options());
				}
			}
		}
		return options;
	}

	private static boolean hasContent(OptionGroup group) {
		if (group instanceof ListOption<?>) return true;
		for (Option<?> option : group.options()) {
			if (!OptionVisibility.isHidden(option) || OptionVisibility.hasSearchAlias(option)) return true;
		}
		return false;
	}

	private static Text groupDescription(OptionGroup group) {
		try {
			return group.description().text();
		} catch (RuntimeException ignored) {
			return Text.empty();
		}
	}

	private static Text optionDescription(Option<?> option) {
		try {
			return option.description().text();
		} catch (RuntimeException ignored) {
			return Text.empty();
		}
	}

}
