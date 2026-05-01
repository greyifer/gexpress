package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.screen.guidebook.GuidebookPageContent;
import cat.rezelyn.watheextended.client.screen.guidebook.GuidebookPageContent.PageResult;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = GuidebookPageContent.class, remap = false)
public abstract class GuidebookPageContentMixin {
	private static final String GEXPRESS_ID_PREFIX = "gexpress:";
	private static final String GEXPRESS_DESC_PREFIX = "gui.watheextended.guidebook.role.desc.gexpress.";

	@Inject(method = "resolve(Ljava/lang/String;Ljava/lang/String;IZ)Lcat/rezelyn/watheextended/client/screen/guidebook/GuidebookPageContent$PageResult;",
		at = @At("HEAD"), cancellable = true)
	private static void gexpress$useLiveRoleDescriptionOverride(String descriptionKey, String id, int page,
			boolean killerSided, CallbackInfoReturnable<PageResult> cir) {
		if (page != 0) return;
		String rolePath = rolePath(descriptionKey, id);
		if (rolePath == null) return;
		String override = GexpressConfig.getRoleDescriptionOverride(rolePath);
		if (override == null || override.isBlank()) return;

		List<Text> lines = new ArrayList<>();
		for (String line : override.split("\\\\n|\\n", -1)) {
			lines.add(GuidebookPageContent.parseLine(line));
		}
		cir.setReturnValue(new PageResult(lines, false));
	}

	private static String rolePath(String descriptionKey, String id) {
		if (id != null && id.startsWith(GEXPRESS_ID_PREFIX)) {
			return id.substring(GEXPRESS_ID_PREFIX.length());
		}
		if (descriptionKey != null && descriptionKey.startsWith(GEXPRESS_DESC_PREFIX)) {
			return descriptionKey.substring(GEXPRESS_DESC_PREFIX.length());
		}
		return null;
	}
}
