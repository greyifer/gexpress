package dev.mapselect.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class DisconnectPacketNoiseMixin {
	@Shadow
	private Channel channel;

	@Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
	private void gexpress$suppressRecursiveDisconnectPacket(ChannelHandlerContext context, Throwable throwable,
			CallbackInfo ci) {
		if (throwable instanceof EncoderException && throwable.getMessage() != null
				&& throwable.getMessage().contains("clientbound/minecraft:disconnect")) {
			if (channel != null && channel.isOpen()) channel.close();
			ci.cancel();
		}
	}
}
