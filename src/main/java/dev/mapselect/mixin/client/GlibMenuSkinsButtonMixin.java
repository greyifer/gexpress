package dev.mapselect.mixin.client;

import dev.mapselect.client.screen.GexpressSkinsCategory;
import io.github.caspe.glib.api.client.menu.GlibMenuScreen;
import io.github.caspe.glib.api.client.ui.GlibButton;
import io.github.caspe.glib.api.client.ui.GlibLoadingSurface;
import io.github.caspe.glib.api.client.ui.GlibLoadingTransitionScreen;
import io.github.caspe.glib.api.client.ui.GlibRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds G'Express cosmetics beside G'Lib's root Options and Friends controls. */
@Mixin(value = GlibMenuScreen.class, remap = false)
public abstract class GlibMenuSkinsButtonMixin extends Screen {
	@Shadow @Final private boolean rootMenu;
	@Unique private GlibButton gexpress$skinsButton;

	protected GlibMenuSkinsButtonMixin(Text title) {
		super(title);
	}

	@Dynamic("GlibMenuScreen is distributed with intermediary Minecraft method names.")
	@Inject(method = "method_25426", at = @At("TAIL"))
	private void gexpress$addSkinsAndInventoryButton(CallbackInfo ci) {
		if (!rootMenu) return;
		GlibRect panel = ((GlibLoadingSurface) (Object) this).glibLoadingSurface();
		gexpress$skinsButton = new GlibButton(panel.x() + panel.width() - 110,
			panel.y() + panel.height() - 52, 22, 22, Text.literal("S"), 0xFFFFD166, () -> {
				Screen origin = (Screen) (Object) this;
				GlibLoadingTransitionScreen.open(origin, "Loading skins & inventory...", 0xFFFFD166,
					() -> GexpressSkinsCategory.createGlibScreen(origin));
			});
		gexpress$skinsButton.setTooltip(Tooltip.of(Text.literal("Skins & Inventory")));
		gexpress$skinsButton.setRenderAlpha(0.0F);
		gexpress$skinsButton.active = false;
		addDrawableChild(gexpress$skinsButton);
	}

	@Inject(method = "updateRootControls", at = @At("TAIL"))
	private void gexpress$animateSkinsAndInventoryButton(float alpha, CallbackInfo ci) {
		if (gexpress$skinsButton == null) return;
		gexpress$skinsButton.setRenderAlpha(alpha);
		gexpress$skinsButton.active = alpha >= 0.98F;
	}
}
