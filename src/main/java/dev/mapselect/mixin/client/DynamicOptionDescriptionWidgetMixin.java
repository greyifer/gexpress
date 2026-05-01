package dev.mapselect.mixin.client;

import dev.isxander.yacl3.gui.DescriptionWithName;
import dev.isxander.yacl3.gui.OptionDescriptionWidget;
import dev.mapselect.client.screen.DynamicOptionDescription;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = OptionDescriptionWidget.class, remap = false)
public abstract class DynamicOptionDescriptionWidgetMixin {
	@Shadow
	private @Nullable DescriptionWithName description;

	@Shadow
	private List<OrderedText> wrappedText;

	@Inject(method = "renderWidget", at = @At("HEAD"))
	private void gexpress$refreshDynamicDescription(DrawContext graphics, int mouseX, int mouseY,
			float delta, CallbackInfo ci) {
		if (description != null && description.description() instanceof DynamicOptionDescription) {
			wrappedText = null;
		}
	}
}
