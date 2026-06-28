package dev.mapselect.modifier;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.modifier.MutedPreferencePayload;
import dev.mapselect.registry.MapSelectModifiers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MutedPreferenceManager {
	private static final Set<UUID> ENABLED = new HashSet<>();
	private static final Set<UUID> AUTO_ASSIGNED = new HashSet<>();

	private MutedPreferenceManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(MutedPreferencePayload.ID, MutedPreferencePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MutedPreferencePayload.ID, (payload, context) ->
			context.server().execute(() -> update(context.player(), payload.enabled())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUuid();
			ENABLED.remove(id);
			AUTO_ASSIGNED.remove(id);
		});
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
			if (world instanceof ServerWorld serverWorld) applyToRound(serverWorld);
		});
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> AUTO_ASSIGNED.clear());
	}

	private static void update(ServerPlayerEntity player, boolean enabled) {
		if (player == null) return;
		UUID id = player.getUuid();
		if (enabled) {
			ENABLED.add(id);
			apply(player);
			return;
		}
		ENABLED.remove(id);
		if (AUTO_ASSIGNED.remove(id)) ModifierUtils.removeIfPresent(player, MapSelectModifiers.MUTED);
	}

	private static void applyToRound(ServerWorld world) {
		if (world == null) return;
		for (ServerPlayerEntity player : world.getPlayers()) apply(player, false);
	}

	private static void apply(ServerPlayerEntity player) {
		apply(player, true);
	}

	private static void apply(ServerPlayerEntity player, boolean requireActiveRound) {
		if (player == null || !ENABLED.contains(player.getUuid())) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null || (requireActiveRound
				&& game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE)) return;
		if (ModifierUtils.addIfMissing(player, MapSelectModifiers.MUTED)) AUTO_ASSIGNED.add(player.getUuid());
	}
}
