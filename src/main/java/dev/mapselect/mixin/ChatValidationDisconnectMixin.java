package dev.mapselect.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ChatValidationDisconnectMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Redirect(
		method = "validateAcknowledgment",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;disconnect(Lnet/minecraft/text/Text;)V")
	)
	private void gexpress$skipChatSignatureDisconnect(ServerPlayNetworkHandler handler, Text reason) {
		if (player != null) {
			player.sendMessage(Text.literal("Chat desynced; skipped one message instead of disconnecting.")
				.formatted(Formatting.YELLOW), true);
		}
	}
}
