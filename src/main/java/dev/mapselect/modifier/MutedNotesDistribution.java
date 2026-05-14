package dev.mapselect.modifier;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.MapSelect;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.agmas.harpymodloader.component.WorldModifierComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MutedNotesDistribution {
	private static final int STARTING_NOTES = 3;
	private static final int CHECK_INTERVAL_TICKS = 10;
	private static final Set<UUID> granted = new HashSet<>();
	private static int ticksUntilNextCheck = 0;

	private MutedNotesDistribution() {}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(MutedNotesDistribution::tick);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;

		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean activeGame = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!activeGame && !GexpressTestState.hasModifierTesters()) {
			if (!granted.isEmpty()) granted.clear();
			ticksUntilNextCheck = 0;
			return;
		}

		WorldModifierComponent modifiers = WorldModifierComponent.KEY.getNullable(world);
		if (modifiers == null) return;

		if (ticksUntilNextCheck > 0) {
			ticksUntilNextCheck--;
			return;
		}
		ticksUntilNextCheck = CHECK_INTERVAL_TICKS - 1;

		for (var player : world.getPlayers()) {
			boolean testingMuted = GexpressTestState.isModifierTester(player, MapSelectModifiers.MUTED);
			if (!activeGame && !testingMuted) continue;
			if (!GameFunctions.isPlayerAliveAndSurvival(player) && !testingMuted) continue;
			UUID id = player.getUuid();
			if (!modifiers.isModifier(player, MapSelectModifiers.MUTED)) continue;

			int missing = STARTING_NOTES - player.getInventory().count(WatheItems.NOTE);
			if (missing <= 0) {
				granted.add(id);
				continue;
			}
			if (granted.contains(id)) {
				granted.remove(id);
				MapSelect.LOGGER.debug("Re-handing missing note(s) to muted player {}", player.getName().getString());
			}
			if (missing > 0) {
				ItemStack stack = WatheItems.NOTE.getDefaultStack();
				stack.setCount(missing);
				if (!player.getInventory().insertStack(stack)) {
					player.dropItem(stack, false);
				}
			}
			granted.add(id);
			MapSelect.LOGGER.debug("Handed {} note(s) to muted player {}", Math.max(0, missing),
				player.getName().getString());
		}
	}
}
