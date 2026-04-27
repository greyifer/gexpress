package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.gamemode.MurderGameMode;
import dev.mapselect.role.PassiveMoney;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

@Mixin(value = MurderGameMode.class, remap = false)
public abstract class PassiveMoneyGraceMixin {
	@Redirect(
		method = "tickServerGameLoop",
		at = @At(value = "INVOKE", target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;")
	)
	private Object gexpress$skipOpeningPassiveMoney(Function<Long, Integer> function, Object tick,
			ServerWorld world, GameWorldComponent game) {
		return PassiveMoney.value(world);
	}
}
