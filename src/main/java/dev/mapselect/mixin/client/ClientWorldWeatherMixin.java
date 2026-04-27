package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.index.WatheParticles;
import dev.mapselect.weather.MapWeatherComponent;
import dev.mapselect.weather.WeatherType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ClientWorld.class)
public abstract class ClientWorldWeatherMixin {

	@Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
	private void mapselect$addMapWeatherParticles(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
		ClientWorld self = (ClientWorld) (Object) this;
		MapWeatherComponent comp = MapWeatherComponent.KEY.getNullable(self);
		if (comp == null) return;
		WeatherType weather = comp.getWeather();
		if (weather == null || weather == WeatherType.NONE) return;

		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) return;

		MapVariablesWorldComponent mapVars = MapVariablesWorldComponent.KEY.getNullable(self);
		if (mapVars == null) return;
		Box playArea = mapVars.getPlayArea();
		if (playArea == null || !playArea.contains(player.getPos())) return;

		ParticleEffect effect;
		if (weather == WeatherType.SNOW) {
			effect = WatheParticles.SNOWFLAKE;
		} else {
			return;
		}

		Random random = player.getRandom();
		Vec3d vel = player.getVelocity();
		int count = 50;
		double horizontalRange = 16.0D;
		double verticalRange = 8.0D;
		for (int i = 0; i < count; i++) {
			double x = player.getX() + (random.nextFloat() * 2f - 1f) * horizontalRange + vel.x;
			double y = player.getY() + (random.nextFloat() * 2f - 1f) * verticalRange + vel.y;
			double z = player.getZ() + (random.nextFloat() * 2f - 1f) * horizontalRange + vel.z;
			BlockPos bp = BlockPos.ofFloored(x, y, z);
			if (self.isSkyVisible(bp) && self.getBlockState(bp).isAir()) {
				self.addParticle(effect, x, y, z, 2.0 + vel.x, vel.y, vel.z);
			}
		}
	}
}
