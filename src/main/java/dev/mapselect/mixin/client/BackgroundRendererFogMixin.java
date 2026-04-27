package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.mapselect.weather.MapWeatherComponent;
import dev.mapselect.weather.WeatherType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BackgroundRenderer.class, priority = 2000)
public abstract class BackgroundRendererFogMixin {

	@WrapOperation(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/CubicSampler;sampleColor(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/CubicSampler$RgbFetcher;)Lnet/minecraft/util/math/Vec3d;"
		)
	)
	private static Vec3d mapselect$overrideFogColor(Vec3d pos, CubicSampler.RgbFetcher fetcher, Operation<Vec3d> original) {
		ClientWorld world = MinecraftClient.getInstance().world;
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (world != null && player != null) {
			MapWeatherComponent comp = MapWeatherComponent.KEY.getNullable(world);
			if (comp != null && comp.hasFogOverride()) {
				MapVariablesWorldComponent mapVars = MapVariablesWorldComponent.KEY.getNullable(world);
				if (mapVars != null) {
					Box playArea = mapVars.getPlayArea();
					if (playArea != null && playArea.contains(player.getPos())) {
						int color = comp.getFogColor();
						return new Vec3d(
							((color >> 16) & 0xFF) / 255.0,
							((color >> 8) & 0xFF) / 255.0,
							(color & 0xFF) / 255.0
						);
					}
				}
			}
		}
		return original.call(pos, fetcher);
	}

	@Inject(
		method = "applyFog",
		at = @At("TAIL")
	)
	private static void mapselect$thickenSandstormFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {
		if (fogType != BackgroundRenderer.FogType.FOG_TERRAIN) return;

		ClientWorld world = MinecraftClient.getInstance().world;
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (world == null || player == null) return;

		MapWeatherComponent comp = MapWeatherComponent.KEY.getNullable(world);
		if (comp == null || comp.getWeather() != WeatherType.SANDSTORM) return;

		MapVariablesWorldComponent mapVars = MapVariablesWorldComponent.KEY.getNullable(world);
		if (mapVars == null) return;
		Box playArea = mapVars.getPlayArea();
		if (playArea == null || !playArea.contains(player.getPos())) return;

		float exposure = world.isSkyVisible(player.getBlockPos()) ? 1f : 0.35f;
		float fogStart = 4f;
		float fogEnd = 18f + (1f - exposure) * 40f;

		RenderSystem.setShaderFogStart(fogStart);
		RenderSystem.setShaderFogEnd(fogEnd);
	}
}
