package dev.mapselect.mixin.client;

import dev.mapselect.client.render.PainterDoorwayFrontClipping;
import net.minecraft.client.gl.GlImportProcessor;
import net.minecraft.client.gl.ShaderStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Mixin(ShaderStage.class)
public abstract class PainterShaderStageClipMixin {
	@Unique
	private static final ThreadLocal<ShaderStage.Type> gexpress$shaderType = new ThreadLocal<>();
	@Unique
	private static final ThreadLocal<String> gexpress$shaderName = new ThreadLocal<>();

	@Inject(method = "load", at = @At("HEAD"))
	private static void gexpress$captureShader(ShaderStage.Type type, String name, InputStream stream,
			String domain, GlImportProcessor processor, CallbackInfoReturnable<Integer> cir) {
		gexpress$shaderType.set(type);
		gexpress$shaderName.set(name);
	}

	@ModifyArg(
		method = "load",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/GlStateManager;glShaderSource(ILjava/util/List;)V",
			remap = false
		),
		index = 1
	)
	private static List<String> gexpress$transformShaderSource(List<String> source) {
		return PainterDoorwayFrontClipping.transformShader(gexpress$shaderType.get(),
			gexpress$shaderName.get(), source);
	}

	@Inject(method = "load", at = @At("RETURN"))
	private static void gexpress$clearShaderCapture(ShaderStage.Type type, String name, InputStream stream,
			String domain, GlImportProcessor processor, CallbackInfoReturnable<Integer> cir) {
		gexpress$shaderType.remove();
		gexpress$shaderName.remove();
	}
}
