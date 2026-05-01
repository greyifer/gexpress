package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.MoodRenderer;
import dev.mapselect.client.ClientSnitchState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MoodRenderer.class, remap = false)
public abstract class SnitchMoodRendererMixin {
	@Inject(method = "renderHud", at = @At("RETURN"))
	private static void gexpress$renderSnitchHud(PlayerEntity player, TextRenderer textRenderer,
			DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		ClientSnitchState.renderMoodOverlay(context, textRenderer);
	}
}
