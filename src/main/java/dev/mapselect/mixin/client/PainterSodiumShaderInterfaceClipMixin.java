package dev.mapselect.mixin.client;

import dev.mapselect.client.render.PainterDoorwayFrontClipping;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat4v;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = DefaultShaderInterface.class, remap = false)
public class PainterSodiumShaderInterfaceClipMixin {
	@Unique
	private GlUniformFloat4v gexpress$clippingEquation;

	@Inject(method = "<init>", at = @At("RETURN"), remap = false)
	private void gexpress$bindClippingUniform(ShaderBindingContext context, ChunkShaderOptions options,
			CallbackInfo ci) {
		gexpress$clippingEquation = context.bindUniformOptional("iportal_ClippingEquation", GlUniformFloat4v::new);
	}

	@Inject(method = "setupState", at = @At("RETURN"), remap = false)
	private void gexpress$uploadClippingUniform(CallbackInfo ci) {
		PainterDoorwayFrontClipping.updateSodiumUniform(gexpress$clippingEquation);
	}
}
