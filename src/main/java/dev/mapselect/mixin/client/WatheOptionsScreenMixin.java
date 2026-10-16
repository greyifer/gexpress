package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.screen.WatheOptionsScreen;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.mapselect.client.screen.GexpressOptionsScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WatheOptionsScreen.class, remap = false)
public abstract class WatheOptionsScreenMixin {

	@Inject(method = "create", at = @At("HEAD"))
	private static void gexpress$prepareTabs(Screen parent, CallbackInfoReturnable<Screen> cir) {
		GexpressOptionsScreen.prepareForMenu();
	}

	@ModifyArg(method = "create", at = @At(value = "INVOKE",
		target = "Ldev/isxander/yacl3/api/YetAnotherConfigLib$Builder;category(Ldev/isxander/yacl3/api/ConfigCategory;)Ldev/isxander/yacl3/api/YetAnotherConfigLib$Builder;", ordinal = 0), index = 0)
	private static dev.isxander.yacl3.api.ConfigCategory gexpress$extendClientTab(dev.isxander.yacl3.api.ConfigCategory original) {
		return dev.mapselect.client.screen.GexpressClientCategory.extend(original);
	}

	@Redirect(method = "create", at = @At(value = "INVOKE",
		target = "Ldev/isxander/yacl3/api/YetAnotherConfigLib$Builder;build()Ldev/isxander/yacl3/api/YetAnotherConfigLib;"))
	private static YetAnotherConfigLib gexpress$addTabs(YetAnotherConfigLib.Builder builder, Screen parent) {
		GexpressOptionsScreen.addWatheTabs(builder, parent);
		return builder.build();
	}

	@ModifyArg(method = "create", at = @At(value = "INVOKE",
		target = "Ldev/isxander/yacl3/api/YetAnotherConfigLib$Builder;save(Ljava/lang/Runnable;)Ldev/isxander/yacl3/api/YetAnotherConfigLib$Builder;"), index = 0)
	private static Runnable gexpress$saveTabs(Runnable original) {
		return () -> {
			GexpressOptionsScreen.finishGlibSave();
			original.run();
		};
	}
}
