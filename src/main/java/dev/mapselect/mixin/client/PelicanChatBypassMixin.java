package dev.mapselect.mixin.client;

import dev.mapselect.client.role.pelican.ClientVultureState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets Pelicans open chat so the belly-popup close buttons remain reachable. */
@Mixin(value = MinecraftClient.class, priority = 500)
public abstract class PelicanChatBypassMixin {
	@Dynamic("Wathe Extended injects this chat-screen restriction into MinecraftClient.")
	@Inject(
		method = "watheextended$restrictChatScreen",
		at = @At("HEAD"),
		cancellable = true,
		remap = false,
		require = 0
	)
	private void gexpress$allowPelicanChat(Screen screen, CallbackInfo setScreenCallback,
			CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (screen instanceof ChatScreen && ClientVultureState.canOpenPelicanChat(client)) {
			// Cancel Wathe Extended's injected helper, not MinecraftClient#setScreen itself.
			ci.cancel();
		}
	}
}
