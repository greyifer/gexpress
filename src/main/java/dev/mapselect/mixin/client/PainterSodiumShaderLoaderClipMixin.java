package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.mapselect.client.render.PainterDoorwayFrontClipping;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderType;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = ShaderLoader.class, remap = false)
public abstract class PainterSodiumShaderLoaderClipMixin {
	@WrapOperation(
		method = "loadShader",
		at = @At(
			value = "INVOKE",
			target = "Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderLoader;getShaderSource(Lnet/minecraft/util/Identifier;)Ljava/lang/String;",
			remap = true
		)
	)
	private static String gexpress$transformSodiumShaderSource(Identifier name, Operation<String> original,
			@Local(argsOnly = true) ShaderType shaderType) {
		String source = original.call(name);
		if (shaderType == ShaderType.VERTEX) {
			return PainterDoorwayFrontClipping.transformSodiumShader(name.toString(), source);
		}
		return source;
	}
}
