package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.screen.guidebook.GuidebookEntry;
import cat.rezelyn.watheextended.client.screen.guidebook.GuidebookEntryBuilder;
import dev.mapselect.MapSelect;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(value = GuidebookEntryBuilder.class, remap = false)
public abstract class MafiaGuidebookCategoryMixin {
	private static final Set<String> GEXPRESS$MAFIA_ROLES = Set.of(
		MapSelect.MOD_ID + ":godfather",
		MapSelect.MOD_ID + ":mafioso",
		MapSelect.MOD_ID + ":janitor"
	);
	private static final Set<String> GEXPRESS$COVENANT_ROLES = Set.of(
		MapSelect.MOD_ID + ":dracula",
		MapSelect.MOD_ID + ":vampire"
	);

	@Inject(method = "buildRoles", at = @At("RETURN"), cancellable = true, require = 0)
	private static void gexpress$moveMafiaRolesToFamilySection(
			CallbackInfoReturnable<List<GuidebookEntry>> cir) {
		List<GuidebookEntry> entries = cir.getReturnValue();
		if (entries == null || entries.isEmpty()) return;

		List<GuidebookEntry> normal = new ArrayList<>(entries.size());
		List<GuidebookEntry> mafia = new ArrayList<>();
		List<GuidebookEntry> covenant = new ArrayList<>();
		for (GuidebookEntry entry : entries) {
			String id = entry == null ? null : entry.id();
			if (id != null && GEXPRESS$MAFIA_ROLES.contains(id)) {
				mafia.add(entry);
			} else if (id != null && GEXPRESS$COVENANT_ROLES.contains(id)) {
				covenant.add(entry);
			} else {
				normal.add(entry);
			}
		}
		if (mafia.isEmpty() && covenant.isEmpty()) return;

		if (!mafia.isEmpty()) {
			normal.add(GuidebookEntry.spacer());
			normal.add(GuidebookEntry.header(
				Text.translatable("gui.gexpress.guidebook.roles.side.mafia").formatted(Formatting.DARK_GRAY),
				0x555555
			));
			normal.addAll(mafia);
		}
		if (!covenant.isEmpty()) {
			normal.add(GuidebookEntry.spacer());
			normal.add(GuidebookEntry.header(
				Text.translatable("gui.gexpress.guidebook.roles.side.covenant").formatted(Formatting.DARK_RED),
				0xB81832
			));
			normal.addAll(covenant);
		}
		cir.setReturnValue(normal);
	}
}
