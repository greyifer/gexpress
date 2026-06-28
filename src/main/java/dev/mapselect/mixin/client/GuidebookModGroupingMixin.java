package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.screen.guidebook.GuidebookEntry;
import cat.rezelyn.watheextended.client.screen.guidebook.GuidebookEntryBuilder;
import cat.rezelyn.watheextended.client.screen.guidebook.GuidebookIcons;
import dev.mapselect.MapSelect;
import dev.mapselect.client.text.GexpressRoleTexts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Mixin(value = GuidebookEntryBuilder.class, remap = false)
public abstract class GuidebookModGroupingMixin {
	@Unique private static final int GEXPRESS$SUBHEADER_COLOR = 0x8A744A;
	@Unique private static final int GEXPRESS$SPECIAL_SUBHEADER_COLOR = 0x7B6C52;
	@Unique private static final int GEXPRESS$MAFIA_COLOR = 0x777777;
	@Unique private static final int GEXPRESS$COVENANT_COLOR = 0x8B0F1F;
	@Unique private static final String GEXPRESS$MAFIA_ICON = "\uE500";
	@Unique private static final String GEXPRESS$COVENANT_ICON = "\uE501";
	@Unique private static final String GEXPRESS$PAINTER_ID = MapSelect.MOD_ID + ":painter";
	@Unique private static final Set<String> GEXPRESS$MAFIA_ROLES = Set.of(
		"godfather", "mafioso", "janitor", "pickpocket", "burglar"
	);
	@Unique private static final Set<String> GEXPRESS$COVENANT_ROLES = Set.of("dracula", "vampire");

	@Inject(method = "buildRoles", at = @At("RETURN"), cancellable = true, require = 0)
	private static void gexpress$groupRolesByMod(CallbackInfoReturnable<List<GuidebookEntry>> cir) {
		List<GuidebookEntry> entries = cir.getReturnValue();
		if (entries == null || entries.isEmpty()) return;
		cir.setReturnValue(gexpress$groupRoleSections(entries));
	}

	@Inject(method = "buildModifiers", at = @At("RETURN"), cancellable = true, require = 0)
	private static void gexpress$groupModifiersByMod(CallbackInfoReturnable<List<GuidebookEntry>> cir) {
		List<GuidebookEntry> entries = cir.getReturnValue();
		if (entries == null || entries.isEmpty()) return;
		List<GuidebookEntry> grouped = new ArrayList<>();
		gexpress$appendModGroups(grouped, entries);
		cir.setReturnValue(grouped);
	}

	@Unique
	private static List<GuidebookEntry> gexpress$groupRoleSections(List<GuidebookEntry> entries) {
		List<GuidebookEntry> grouped = new ArrayList<>(entries.size() + 16);
		List<GuidebookEntry> sectionEntries = new ArrayList<>();
		List<GuidebookEntry> mafiaEntries = new ArrayList<>();
		List<GuidebookEntry> covenantEntries = new ArrayList<>();
		boolean hasHeader = false;

		for (GuidebookEntry entry : entries) {
			entry = gexpress$styleSpecialRole(entry);
			if (entry != null && entry.isHeader()) {
				gexpress$appendRoleSection(grouped, sectionEntries, mafiaEntries, covenantEntries);
				sectionEntries.clear();
				if (hasHeader) grouped.add(GuidebookEntry.spacer());
				grouped.add(entry);
				hasHeader = true;
			} else {
				sectionEntries.add(entry);
			}
		}
		gexpress$appendRoleSection(grouped, sectionEntries, mafiaEntries, covenantEntries);
		gexpress$appendSpecialRoleSection(grouped, "Mafia Roles", GEXPRESS$MAFIA_ICON, GEXPRESS$MAFIA_COLOR, mafiaEntries);
		gexpress$appendSpecialRoleSection(grouped, "Covenant Roles", GEXPRESS$COVENANT_ICON, GEXPRESS$COVENANT_COLOR,
			covenantEntries);
		return grouped;
	}

	@Unique
	private static GuidebookEntry gexpress$styleSpecialRole(GuidebookEntry entry) {
		if (entry == null || !GEXPRESS$PAINTER_ID.equals(entry.id())) return entry;
		MutableText row = GuidebookIcons.icon(entry.active() ? "enabled" : "disabled").copy()
			.append(Text.literal(" "))
			.append(GexpressRoleTexts.painterName(entry.displayTitle()));
		MutableText title = GexpressRoleTexts.painterName(entry.displayTitle());
		return new GuidebookEntry(row, entry.color(), entry.isHeader(), entry.id(), entry.descriptionKey(), title,
			entry.active(), entry.killerSided());
	}

	@Unique
	private static void gexpress$appendModGroups(List<GuidebookEntry> output, List<GuidebookEntry> entries) {
		gexpress$appendModGroups(output, entries, true);
	}

	@Unique
	private static void gexpress$appendModGroups(List<GuidebookEntry> output, List<GuidebookEntry> entries,
			boolean splitGexpressFamilies) {
		if (entries == null || entries.isEmpty()) return;

		Map<String, List<GuidebookEntry>> byNamespace = new LinkedHashMap<>();
		List<GuidebookEntry> ungrouped = new ArrayList<>();
		for (GuidebookEntry entry : entries) {
			if (entry == null || entry.text().getString().isEmpty()) continue;
			String namespace = gexpress$namespace(entry);
			if (namespace == null || namespace.isBlank()) {
				ungrouped.add(entry);
			} else {
				byNamespace.computeIfAbsent(namespace, ignored -> new ArrayList<>()).add(entry);
			}
		}

		for (Map.Entry<String, List<GuidebookEntry>> group : byNamespace.entrySet()) {
			output.add(GuidebookEntry.spacer());
			output.add(gexpress$modSubheader(group.getKey()));
			if (splitGexpressFamilies && MapSelect.MOD_ID.equals(group.getKey())) {
				gexpress$appendGexpressRoleSubgroups(output, group.getValue());
			} else {
				output.addAll(group.getValue());
			}
		}
		if (!ungrouped.isEmpty()) {
			output.add(GuidebookEntry.spacer());
			output.addAll(ungrouped);
		}
	}

	@Unique
	private static void gexpress$appendRoleSection(List<GuidebookEntry> output, List<GuidebookEntry> sectionEntries,
			List<GuidebookEntry> mafiaEntries, List<GuidebookEntry> covenantEntries) {
		if (sectionEntries == null || sectionEntries.isEmpty()) return;
		List<GuidebookEntry> regular = new ArrayList<>(sectionEntries.size());
		for (GuidebookEntry entry : sectionEntries) {
			if (gexpress$isMafiaRole(entry)) {
				mafiaEntries.add(entry);
			} else if (gexpress$isCovenantRole(entry)) {
				covenantEntries.add(entry);
			} else {
				regular.add(entry);
			}
		}
		gexpress$appendModGroups(output, regular);
	}

	@Unique
	private static void gexpress$appendSpecialRoleSection(List<GuidebookEntry> output, String label, String icon,
			int color, List<GuidebookEntry> entries) {
		if (entries == null || entries.isEmpty()) return;
		if (!output.isEmpty()) output.add(GuidebookEntry.spacer());
		output.add(GuidebookEntry.header(gexpress$familyHeader(label, icon, color), color));
		gexpress$appendModGroups(output, entries, false);
	}

	@Unique
	private static Text gexpress$familyHeader(String label, String icon, int color) {
		return Text.literal(icon).formatted(Formatting.WHITE)
			.append(Text.literal(" " + label).styled(style -> style.withBold(true).withColor(color)));
	}

	@Unique
	private static GuidebookEntry gexpress$modSubheader(String namespace) {
		Text text = Text.literal(" " + gexpress$modName(namespace)).formatted(Formatting.ITALIC);
		return new GuidebookEntry(text, GEXPRESS$SUBHEADER_COLOR, false, null, null, text, true, false);
	}

	@Unique
	private static GuidebookEntry gexpress$specialSubheader(String label) {
		Text text = Text.literal("  " + label).formatted(Formatting.ITALIC);
		int color = "Mafia".equals(label) ? GEXPRESS$MAFIA_COLOR
			: "Covenant".equals(label) ? GEXPRESS$COVENANT_COLOR
			: GEXPRESS$SPECIAL_SUBHEADER_COLOR;
		return new GuidebookEntry(text, color, false, null, null, text, true, false);
	}

	@Unique
	private static void gexpress$appendGexpressRoleSubgroups(List<GuidebookEntry> output, List<GuidebookEntry> entries) {
		List<GuidebookEntry> regular = new ArrayList<>();
		List<GuidebookEntry> mafia = new ArrayList<>();
		List<GuidebookEntry> covenant = new ArrayList<>();
		for (GuidebookEntry entry : entries) {
			String path = gexpress$idPath(entry);
			if (GEXPRESS$MAFIA_ROLES.contains(path)) {
				mafia.add(entry);
			} else if (GEXPRESS$COVENANT_ROLES.contains(path)) {
				covenant.add(entry);
			} else {
				regular.add(entry);
			}
		}
		output.addAll(regular);
		if (!mafia.isEmpty()) {
			output.add(GuidebookEntry.spacer());
			output.add(gexpress$specialSubheader("Mafia"));
			output.addAll(mafia);
		}
		if (!covenant.isEmpty()) {
			output.add(GuidebookEntry.spacer());
			output.add(gexpress$specialSubheader("Covenant"));
			output.addAll(covenant);
		}
	}

	@Unique
	private static String gexpress$namespace(GuidebookEntry entry) {
		String id = entry.id();
		if (id != null) {
			int split = id.indexOf(':');
			if (split > 0) return id.substring(0, split);
		}
		return gexpress$namespaceFromDescription(entry.descriptionKey());
	}

	@Unique
	private static boolean gexpress$isMafiaRole(GuidebookEntry entry) {
		return MapSelect.MOD_ID.equals(gexpress$namespace(entry))
			&& GEXPRESS$MAFIA_ROLES.contains(gexpress$idPath(entry));
	}

	@Unique
	private static boolean gexpress$isCovenantRole(GuidebookEntry entry) {
		return MapSelect.MOD_ID.equals(gexpress$namespace(entry))
			&& GEXPRESS$COVENANT_ROLES.contains(gexpress$idPath(entry));
	}

	@Unique
	private static String gexpress$idPath(GuidebookEntry entry) {
		if (entry == null) return "";
		String id = entry.id();
		if (id != null) {
			int split = id.indexOf(':');
			if (split >= 0 && split + 1 < id.length()) return id.substring(split + 1);
			if (!id.isBlank()) return id;
		}
		String key = entry.descriptionKey();
		if (key == null) return "";
		int split = key.lastIndexOf('.');
		return split >= 0 && split + 1 < key.length() ? key.substring(split + 1) : key;
	}

	@Unique
	private static String gexpress$namespaceFromDescription(String descriptionKey) {
		if (descriptionKey == null) return null;
		String prefix = "gui.watheextended.guidebook.";
		if (!descriptionKey.startsWith(prefix)) return null;
		String rest = descriptionKey.substring(prefix.length());
		String rolePrefix = "role.desc.";
		String modifierPrefix = "modifier.desc.";
		if (rest.startsWith(rolePrefix)) rest = rest.substring(rolePrefix.length());
		else if (rest.startsWith(modifierPrefix)) rest = rest.substring(modifierPrefix.length());
		else return null;
		int split = rest.indexOf('.');
		return split > 0 ? rest.substring(0, split) : null;
	}

	@Unique
	private static String gexpress$modName(String namespace) {
		if (MapSelect.MOD_ID.equals(namespace)) return "G'Express";
		return FabricLoader.getInstance().getModContainer(namespace)
			.map(container -> container.getMetadata().getName())
			.filter(name -> name != null && !name.isBlank())
			.orElseGet(() -> gexpress$titleCase(namespace));
	}

	@Unique
	private static String gexpress$titleCase(String namespace) {
		String[] parts = namespace.replace('-', '_').split("_+");
		StringBuilder builder = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) continue;
			if (!builder.isEmpty()) builder.append(' ');
			builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			if (part.length() > 1) builder.append(part.substring(1));
		}
		return builder.isEmpty() ? namespace : builder.toString();
	}
}
