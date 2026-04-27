package dev.mapselect.modifier;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class LoversManager {
	private static final Identifier LOVERS_ID = Identifier.of("stupid_express", "lovers");
	private static final Identifier BROKEN_HEART = Identifier.of("stupid_express", "broken_heart");
	private static final Set<UUID> cascading = new HashSet<>();

	private LoversManager() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(LoversManager::afterDeath);
	}

	private static void afterDeath(LivingEntity entity, net.minecraft.entity.damage.DamageSource source) {
		if (!(entity instanceof ServerPlayerEntity dead) || !(dead.getWorld() instanceof ServerWorld world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return;

		Modifier lovers = loversModifier();
		if (lovers == null) return;
		WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(world);
		if (!modifiers.isModifier(dead, lovers) || !cascading.add(dead.getUuid())) return;
		try {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (player == dead || !modifiers.isModifier(player, lovers)) continue;
				if (!GameFunctions.isPlayerAliveAndSurvival(player)) continue;
				GameFunctions.killPlayer(player, true, null, BROKEN_HEART);
			}
		} finally {
			cascading.remove(dead.getUuid());
		}
	}

	private static Modifier loversModifier() {
		for (Modifier modifier : HMLModifiers.MODIFIERS) {
			if (modifier != null && LOVERS_ID.equals(modifier.identifier())) {
				return modifier;
			}
		}
		return null;
	}
}
