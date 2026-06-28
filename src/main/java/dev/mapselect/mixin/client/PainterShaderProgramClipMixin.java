package dev.mapselect.mixin.client;

import dev.mapselect.client.render.PainterDoorwayFrontClipping;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ShaderProgram.class)
public abstract class PainterShaderProgramClipMixin {
	@Shadow
	@Final
	private List<GlUniform> uniforms;
	@Shadow
	@Final
	private String name;

	@Inject(method = "loadReferences", at = @At("HEAD"))
	private void gexpress$addClipUniform(CallbackInfo ci) {
		if (PainterDoorwayFrontClipping.shouldAddUniform(name)) {
			for (GlUniform uniform : uniforms) {
				if ("iportal_ClippingEquation".equals(uniform.getName())) return;
			}
			uniforms.add(new GlUniform("iportal_ClippingEquation", 7, 4, (ShaderProgram) (Object) this));
		}
	}

	@Inject(method = "initializeUniforms", at = @At("TAIL"))
	private void gexpress$updateClipUniform(VertexFormat.DrawMode drawMode, Matrix4f viewMatrix,
			Matrix4f projectionMatrix, Window window, CallbackInfo ci) {
		PainterDoorwayFrontClipping.updateShader((ShaderProgram) (Object) this);
	}
}
