package dev.mapselect.mixin.client;

import com.google.common.collect.ImmutableSet;
import dev.mapselect.client.render.PainterDoorwayFrontClipping;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat4v;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.programs.SodiumShader;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Supplier;

@Pseudo
@Mixin(value = SodiumShader.class, remap = false)
public class PainterIrisSodiumShaderClipMixin {
	@Unique
	private GlUniformFloat4v gexpress$clippingEquation;

	@Inject(method = "<init>", at = @At("RETURN"), remap = false)
	private void gexpress$bindClippingUniform(IrisRenderingPipeline pipeline, SodiumPrograms.Pass pass,
			ShaderBindingContext context, int handle, BlendModeOverride blendModeOverride,
			List<BufferBlendOverride> bufferBlendOverrides, CustomUniforms customUniforms,
			Supplier<ImmutableSet<Integer>> flipState, float alphaTest, boolean containsTessellation,
			CallbackInfo ci) {
		gexpress$clippingEquation = context.bindUniformOptional("iportal_ClippingEquation", GlUniformFloat4v::new);
	}

	@Inject(method = "setupState", at = @At("RETURN"), remap = false)
	private void gexpress$uploadClippingUniform(CallbackInfo ci) {
		PainterDoorwayFrontClipping.updateSodiumUniform(gexpress$clippingEquation);
	}
}
