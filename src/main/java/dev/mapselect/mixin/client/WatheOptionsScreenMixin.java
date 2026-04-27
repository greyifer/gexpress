package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.screen.WatheOptionsScreen;
import dev.mapselect.client.screen.GexpressOptionsScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WatheOptionsScreen.class, remap = false)
public abstract class WatheOptionsScreenMixin {

	@Inject(method = "create", at = @At("HEAD"), cancellable = true)
	private static void gexpress$redirectToGexpressScreen(Screen parent, CallbackInfoReturnable<Screen> cir) {
		cir.setReturnValue(GexpressOptionsScreen.create(parent));
	}
}
