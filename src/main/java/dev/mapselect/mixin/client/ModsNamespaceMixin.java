package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.screen.config.ScreenUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ScreenUtils#modsNamespace(String) is a hardcoded switch over 5 known mod ids. Anything outside
 * that set falls through a lossy default branch that ends up returning just the stripped "X" suffix
 * for our id, which is why our group label read "X" instead of "G'Express" in the role and
 * modifier sections. Intercept the call for our mod id and return the proper display name.
 */
@Mixin(value = ScreenUtils.class, remap = false)
public abstract class ModsNamespaceMixin {
	@Inject(method = "modsNamespace", at = @At("HEAD"), cancellable = true)
	private static void gexpress$displayName(String id, CallbackInfoReturnable<String> cir) {
		if ("gexpress".equals(id)) {
			cir.setReturnValue("G'Express");
		}
	}
}
