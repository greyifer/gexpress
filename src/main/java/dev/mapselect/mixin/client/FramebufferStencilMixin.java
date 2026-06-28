package dev.mapselect.mixin.client;

import dev.mapselect.client.render.GexpressStencilFramebuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Objects;

@Mixin(Framebuffer.class)
public abstract class FramebufferStencilMixin implements GexpressStencilFramebuffer {
	private boolean gexpress$stencilBufferEnabled;

	@Shadow
	public int textureWidth;

	@Shadow
	public int textureHeight;

	@Shadow
	public abstract void resize(int width, int height, boolean getError);

	@Inject(method = "<init>", at = @At("RETURN"))
	private void gexpress$onInit(boolean useDepthAttachment, CallbackInfo ci) {
		gexpress$stencilBufferEnabled = false;
	}

	// Adapted from Immersive Portals' Apache-2.0 stencil framebuffer hook.
	@ModifyArgs(
		method = "initFbo",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V",
			remap = false
		)
	)
	private void gexpress$useDepthStencilTexture(Args args) {
		if (gexpress$stencilBufferEnabled && Objects.equals(args.get(2), GL11.GL_DEPTH_COMPONENT)) {
			args.set(2, GL30.GL_DEPTH24_STENCIL8);
			args.set(6, ARBFramebufferObject.GL_DEPTH_STENCIL);
			args.set(7, GL30.GL_UNSIGNED_INT_24_8);
		}
	}

	@ModifyArgs(
		method = "initFbo",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glFramebufferTexture2D(IIIII)V",
			remap = false
		)
	)
	private void gexpress$attachDepthStencilTexture(Args args) {
		if (gexpress$stencilBufferEnabled && Objects.equals(args.get(1), GL30.GL_DEPTH_ATTACHMENT)) {
			args.set(1, GL30.GL_DEPTH_STENCIL_ATTACHMENT);
		}
	}

	@Override
	public boolean gexpress$isStencilBufferEnabled() {
		return gexpress$stencilBufferEnabled;
	}

	@Override
	public void gexpress$setStencilBufferEnabledAndReload(boolean enabled) {
		if (gexpress$stencilBufferEnabled == enabled) return;
		gexpress$stencilBufferEnabled = enabled;
		if (textureWidth > 0 && textureHeight > 0) {
			resize(textureWidth, textureHeight, MinecraftClient.IS_SYSTEM_MAC);
		}
	}
}
