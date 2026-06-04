package dev.mapselect.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.harpymodloader.modded_murder.ModdedMurderGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Mixin(value = ModdedMurderGameMode.class, remap = false)
public abstract class HmlModifierRangeMixin {
	@Unique
	private static final ThreadLocal<Map<UUID, Integer>> GEXPRESS_MODIFIER_CAPS =
		ThreadLocal.withInitial(Map::of);

	@Inject(method = "assignModifiers", at = @At("HEAD"))
	private void gexpress$rollModifierCaps(int modifierAmount, ServerWorld world, GameWorldComponent game,
			List<ServerPlayerEntity> players, CallbackInfo ci) {
		int max = Math.max(0, GexpressConfig.getMaxModifiersPerPlayer());
		Map<UUID, Integer> caps = new HashMap<>();
		if (players != null) {
			for (ServerPlayerEntity player : players) {
				if (player != null) caps.put(player.getUuid(), max <= 0 ? 0 : ThreadLocalRandom.current().nextInt(max + 1));
			}
		}
		GEXPRESS_MODIFIER_CAPS.set(caps);
	}

	@Inject(method = "assignModifiers", at = @At("RETURN"))
	private void gexpress$clearModifierCaps(int modifierAmount, ServerWorld world, GameWorldComponent game,
			List<ServerPlayerEntity> players, CallbackInfo ci) {
		GEXPRESS_MODIFIER_CAPS.remove();
	}

	@ModifyExpressionValue(method = "lambda$assignModifiers$1", at = @At(value = "FIELD",
		target = "Lorg/agmas/harpymodloader/config/HarpyModLoaderConfig;modifierMaximum:I"))
	private static int gexpress$useRolledModifierCap(int original, @Local(ordinal = 0) ServerPlayerEntity target) {
		if (target == null) return Math.max(0, GexpressConfig.getMaxModifiersPerPlayer());
		return Math.max(0, GEXPRESS_MODIFIER_CAPS.get()
			.getOrDefault(target.getUuid(), GexpressConfig.getMaxModifiersPerPlayer()));
	}
}
