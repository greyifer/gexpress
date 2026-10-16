package dev.mapselect.modifier;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.MapSelect;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.Modifier;

public final class TallManager {
	private static final Identifier SCALE_MODIFIER_ID = Identifier.of(MapSelect.MOD_ID, "tall_scale");
	private static final double SCALE_BONUS = 0.33D;
	private static final int CHECK_INTERVAL_TICKS = 10;
	private static int ticksUntilNextCheck;

	private TallManager() {}

	public static void register() {
		ModifierAssigned.EVENT.register((player, modifier) -> {
			if (isTall(modifier)) applyTallScale(player);
		});
		ModifierRemoved.EVENT.register((player, modifier) -> {
			if (isTall(modifier)) removeTallScale(player);
		});
		ServerTickEvents.END_WORLD_TICK.register(TallManager::tick);
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;
		if (ticksUntilNextCheck > 0) {
			ticksUntilNextCheck--;
			return;
		}
		ticksUntilNextCheck = CHECK_INTERVAL_TICKS - 1;

		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean activeGame = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		WorldModifierComponent modifiers = WorldModifierComponent.KEY.getNullable(world);
		for (ServerPlayerEntity player : world.getPlayers()) {
			boolean testing = GexpressTestState.isModifierTester(player, MapSelectModifiers.TALL);
			boolean enabled = (activeGame || testing)
				&& (GameFunctions.isPlayerAliveAndSurvival(player) || testing)
				&& ((modifiers != null && modifiers.isModifier(player, MapSelectModifiers.TALL)) || testing);
			if (enabled) {
				applyTallScale(player);
			} else {
				removeTallScale(player);
			}
		}
	}

	private static void applyTallScale(PlayerEntity player) {
		if (player == null) return;
		EntityAttributeInstance scale = player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		if (scale == null) return;
		scale.removeModifier(SCALE_MODIFIER_ID);
		scale.addPersistentModifier(new EntityAttributeModifier(SCALE_MODIFIER_ID, SCALE_BONUS,
			EntityAttributeModifier.Operation.ADD_VALUE));
		recalculateDimensions(player);
	}

	private static void removeTallScale(PlayerEntity player) {
		if (player == null) return;
		EntityAttributeInstance scale = player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		if (scale == null) return;
		if (!scale.hasModifier(SCALE_MODIFIER_ID)) return;
		scale.removeModifier(SCALE_MODIFIER_ID);
		recalculateDimensions(player);
	}

	private static void recalculateDimensions(PlayerEntity player) {
		player.calculateDimensions();
		player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
	}

	private static boolean isTall(Modifier modifier) {
		return modifier != null && modifier.identifier() != null
			&& MapSelectModifiers.TALL_ID.equals(modifier.identifier());
	}
}
