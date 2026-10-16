package dev.mapselect.mixin.client;

import dev.mapselect.client.role.pelican.ClientPelicanChatPopups;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class PelicanChatScreenMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void gexpress$renderPelicanChatPopups(DrawContext context, int mouseX, int mouseY,
			float delta, CallbackInfo ci) {
		ClientPelicanChatPopups.renderOverChat(context, mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void gexpress$closePelicanChatPopup(double mouseX, double mouseY, int button,
			CallbackInfoReturnable<Boolean> cir) {
		if (ClientPelicanChatPopups.handleChatClick(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
		}
	}
}
