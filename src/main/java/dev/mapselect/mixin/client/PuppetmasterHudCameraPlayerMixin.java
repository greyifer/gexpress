package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientPuppetmasterState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InGameHud.class)
public abstract class PuppetmasterHudCameraPlayerMixin {
	@Inject(method = "getCameraPlayer", at = @At("HEAD"), cancellable = true)
	private void gexpress$useControllerHudInventory(CallbackInfoReturnable<PlayerEntity> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientPuppetmasterState.isLocalController(client) && client.player != null) {
			cir.setReturnValue(client.player);
		}
	}
}
