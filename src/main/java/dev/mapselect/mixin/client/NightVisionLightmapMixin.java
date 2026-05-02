package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientNightVisionState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public abstract class NightVisionLightmapMixin {
	@Shadow
	@Final
	private NativeImage image;

	@Shadow
	@Final
	private NativeImageBackedTexture texture;

	@Inject(method = "update", at = @At("RETURN"))
	private void gexpress$softNightVision(float delta, CallbackInfo ci) {
		if (!ClientNightVisionState.hasNightVision()) return;
		float amount = 0.68F * ClientNightVisionState.strength();
		if (amount <= 0.01F) return;

		boolean changed = false;
		for (int sky = 0; sky < 16; sky++) {
			for (int block = 0; block < 16; block++) {
				int color = image.getColor(block, sky);
				int r = color & 0xFF;
				int g = (color >> 8) & 0xFF;
				int b = (color >> 16) & 0xFF;
				int max = Math.max(r, Math.max(g, b));
				if (max >= 220) continue;

				int boostedR;
				int boostedG;
				int boostedB;
				if (max <= 0) {
					boostedR = boostedG = boostedB = 220;
				} else {
					float scale = 220.0F / (float) max;
					boostedR = Math.min(220, Math.round(r * scale));
					boostedG = Math.min(220, Math.round(g * scale));
					boostedB = Math.min(220, Math.round(b * scale));
				}

				int nr = blend(r, boostedR, amount);
				int ng = blend(g, boostedG, amount);
				int nb = blend(b, boostedB, amount);
				if (nr != r || ng != g || nb != b) {
					image.setColor(block, sky, (color & 0xFF000000) | (nb << 16) | (ng << 8) | nr);
					changed = true;
				}
			}
		}
		if (changed) {
			texture.upload();
		}
	}

	private static int blend(int from, int to, float amount) {
		return Math.max(0, Math.min(255, Math.round(from + (to - from) * amount)));
	}
}
