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

			int notes = player.getInventory().count(WatheItems.NOTE);
			if (notes > STARTING_NOTES) trimNotesToStartingCount(player);
			if (notes >= STARTING_NOTES) {
				granted.add(id);
				continue;
			}
			if (granted.contains(id)) continue;

			int missing = STARTING_NOTES - notes;
			ItemStack stack = WatheItems.NOTE.getDefaultStack();
			stack.setCount(missing);
			if (!player.getInventory().insertStack(stack)) {
				MapSelect.LOGGER.debug("Skipped muted note grant for {}; inventory is full",
					player.getName().getString());
				continue;
			}
			granted.add(id);
			MapSelect.LOGGER.debug("Handed {} note(s) to muted player {}", Math.max(0, missing),
				player.getName().getString());
		}
	}

	private static void trimNotesToStartingCount(net.minecraft.server.network.ServerPlayerEntity player) {
		int remainingAllowed = STARTING_NOTES;
		boolean changed = false;
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (stack.isEmpty() || !stack.isOf(WatheItems.NOTE)) continue;
			if (remainingAllowed <= 0) {
				player.getInventory().setStack(slot, ItemStack.EMPTY);
				changed = true;
				continue;
			}
			int keep = Math.min(stack.getCount(), remainingAllowed);
			remainingAllowed -= keep;
			if (stack.getCount() != keep) {
				stack.setCount(keep);
				changed = true;
			}
		}
		if (changed) player.playerScreenHandler.syncState();
	}
}
