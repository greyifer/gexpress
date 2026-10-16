package dev.mapselect.mixin.client;

import dev.mapselect.client.GexpressCreativeSectionScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class GexpressCreativeSlotAnimationMixin {
	@Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
	private void gexpress$skipAnimatedCreativeSlot(DrawContext context, Slot slot, CallbackInfo ci) {
		if ((Object) this instanceof GexpressCreativeSectionScreen sectionScreen
				&& sectionScreen.gexpress$shouldSkipCreativeSlot(slot)) {
			ci.cancel();
		}
	}
}
