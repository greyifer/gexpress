package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.api.GameMode;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import dev.mapselect.game.GexpressGameModes;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MoodRenderer.class, remap = false)
public abstract class GexpressCustomModeMoodRendererMixin {
	@WrapOperation(
		method = "renderHud",
		at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;getGameMode()Ldev/doctor4t/wathe/api/GameMode;")
	)
	private static GameMode gexpress$renderMoodHudForCustomModes(GameWorldComponent game,
			Operation<GameMode> original) {
		if (GexpressGameModes.isAmnesia(game) || GexpressGameModes.isTakeover(game)) {
			return WatheGameModes.MURDER;
		}
		return original.call(game);
	}

	@ModifyExpressionValue(
		method = "renderHud",
		at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/api/Role;getMoodType()Ldev/doctor4t/wathe/api/Role$MoodType;")
	)
	private static Role.MoodType gexpress$showCustomModeSanity(Role.MoodType original) {
		MinecraftClient client = MinecraftClient.getInstance();
		GameWorldComponent game = client == null || client.world == null
			? null : GameWorldComponent.KEY.getNullable(client.world);
		if (GexpressGameModes.isAmnesia(game) || GexpressGameModes.isTakeover(game)) {
			return Role.MoodType.REAL;
		}
		return original;
	}
}
