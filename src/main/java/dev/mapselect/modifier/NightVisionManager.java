package dev.mapselect.modifier;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.network.NightVisionSyncPayload;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.agmas.harpymodloader.component.WorldModifierComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NightVisionManager {
	private static final int CHECK_INTERVAL_TICKS = 10;
	private static final int EFFECT_DURATION_TICKS = 600;
	private static final Map<UUID, Boolean> lastState = new HashMap<>();
	private static int ticksUntilNextCheck = 0;

	private NightVisionManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(NightVisionSyncPayload.ID, NightVisionSyncPayload.CODEC);
		ServerTickEvents.END_WORLD_TICK.register(NightVisionManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.getPlayer() != null) lastState.remove(handler.getPlayer().getUuid());
		});
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;

		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean activeGame = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!activeGame && !GexpressTestState.hasModifierTesters()) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				UUID id = player.getUuid();
				if (Boolean.TRUE.equals(lastState.get(id))) {
					ServerPlayNetworking.send(player, new NightVisionSyncPayload(false));
				}
				clearVanillaNightVision(player);
				lastState.put(id, false);
			}
			ticksUntilNextCheck = 0;
			return;
		}

		if (ticksUntilNextCheck > 0) {
			ticksUntilNextCheck--;
			return;
		}
		ticksUntilNextCheck = CHECK_INTERVAL_TICKS - 1;

		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(world);
		for (ServerPlayerEntity player : world.getPlayers()) {
			boolean testing = GexpressTestState.isModifierTester(player, MapSelectModifiers.NIGHT_VISION);
			boolean current = (activeGame || testing)
				&& (GameFunctions.isPlayerAliveAndSurvival(player) || testing)
				&& ((mods != null && mods.isModifier(player, MapSelectModifiers.NIGHT_VISION)) || testing);
			applyVanillaNightVision(player, current);
			UUID id = player.getUuid();
			Boolean previous = lastState.get(id);
			if (previous == null || previous.booleanValue() != current) {
				ServerPlayNetworking.send(player, new NightVisionSyncPayload(current));
				lastState.put(id, current);
			}
		}
	}

	private static void applyVanillaNightVision(ServerPlayerEntity player, boolean enabled) {
		if (enabled) {
			StatusEffectInstance current = player.getStatusEffect(StatusEffects.NIGHT_VISION);
			if (current == null || current.getDuration() < EFFECT_DURATION_TICKS / 2
					|| current.shouldShowParticles() || current.shouldShowIcon()) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION,
					EFFECT_DURATION_TICKS, 0, false, false, false));
			}
		} else {
			clearVanillaNightVision(player);
		}
	}

	private static void clearVanillaNightVision(ServerPlayerEntity player) {
		if (player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
			player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
	}

}
