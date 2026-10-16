package dev.mapselect.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.client.gl.Framebuffer;

import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.glGetFramebufferAttachmentParameteri;

/**
 * Vanilla framebuffer view over a Veil {@link AdvancedFbo}.
 *
 * Minecraft's world renderer asks the client for a vanilla framebuffer during
 * several render layers. This adapter keeps those calls on the Veil-owned FBO
 * without transferring resource ownership to vanilla.
 */
public final class VeilFramebuffer extends Framebuffer {
	private final AdvancedFbo delegate;

	public VeilFramebuffer(AdvancedFbo delegate) {
		super(delegate != null && delegate.hasDepthAttachment());
		if (delegate == null) throw new IllegalArgumentException("delegate");
		this.delegate = delegate;
		this.textureWidth = delegate.getWidth();
		this.textureHeight = delegate.getHeight();
		this.viewportWidth = delegate.getWidth();
		this.viewportHeight = delegate.getHeight();
		this.fbo = delegate.getId();
		readAttachmentIds();
	}

	private void readAttachmentIds() {
		RenderSystem.assertOnRenderThreadOrInit();
		int previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
		int previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
		delegate.bind(false);
		colorAttachment = glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER,
			GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
		if (useDepthAttachment) {
			int point = delegate.hasStencilAttachment() ? GL_DEPTH_STENCIL_ATTACHMENT : GL_DEPTH_ATTACHMENT;
			depthAttachment = glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER,
				point, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
		}
		GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead);
		GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw);
	}

	@Override
	public void resize(int width, int height, boolean getError) {
		if (width != textureWidth || height != textureHeight) {
			throw new UnsupportedOperationException("Veil framebuffers must be recreated to resize");
		}
	}

	@Override
	public void initFbo(int width, int height, boolean getError) {
		resize(width, height, getError);
	}

	@Override
	public void delete() {
		// The AdvancedFbo owner releases the attachments.
	}

	@Override
	public void beginRead() {
		RenderSystem.setShaderTexture(0, colorAttachment);
	}

	@Override
	public void endRead() {
		RenderSystem.setShaderTexture(0, 0);
	}

	@Override
	public void beginWrite(boolean setViewport) {
		delegate.bindDraw(setViewport);
	}

	@Override
	public void endWrite() {
		AdvancedFbo.unbindDraw();
	}

	@Override
	public void clear(boolean getError) {
		delegate.clear(0.0F, 0.0F, 0.0F, 0.0F,
			GL_COLOR_BUFFER_BIT | (useDepthAttachment ? GL_DEPTH_BUFFER_BIT : 0));
	}
}
