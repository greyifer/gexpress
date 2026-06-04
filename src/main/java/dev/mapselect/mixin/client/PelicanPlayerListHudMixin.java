package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientVultureState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class PelicanPlayerListHudMixin {
	@Inject(method = "renderPlayerList", at = @At("HEAD"), cancellable = true)
	private void gexpress$hidePlayerListInPelicanBody(DrawContext context, RenderTickCounter tickCounter,
			CallbackInfo ci) {
		if (ClientVultureState.isLocalStashed(MinecraftClient.getInstance())) {
			ci.cancel();
		}
	}
}
