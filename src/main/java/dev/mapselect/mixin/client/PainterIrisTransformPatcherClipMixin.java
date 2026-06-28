package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mapselect.client.render.PainterDoorwayFrontClipping;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.Map;

@Pseudo
@Mixin(value = TransformPatcher.class, remap = false)
public abstract class PainterIrisTransformPatcherClipMixin {
	@ModifyReturnValue(method = {"patchSodium", "patchVanilla"}, at = @At("RETURN"), remap = false)
	private static Map<PatchShaderType, String> gexpress$transformIrisTerrainShader(
			Map<PatchShaderType, String> shaders) {
		if (shaders == null) return null;
		String vertex = shaders.get(PatchShaderType.VERTEX);
		String transformed = PainterDoorwayFrontClipping.transformIrisPatchedShader(vertex);
		if (transformed == vertex) return shaders;
		Map<PatchShaderType, String> copy = new HashMap<>(shaders);
		copy.put(PatchShaderType.VERTEX, transformed);
		return copy;
	}
}
