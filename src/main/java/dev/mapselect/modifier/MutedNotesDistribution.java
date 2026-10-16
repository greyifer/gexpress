package dev.mapselect.modifier;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.MapSelect;
import dev.mapselect.game.RoundLifecycleHooks;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.agmas.harpymodloader.component.WorldModifierComponent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MutedNotesDistribution {
	private static final int STARTING_NOTES = 3;
	private static final int CHECK_INTERVAL_TICKS = 10;
	private static final Map<RegistryKey<World>, Set<UUID>> grantedByWorld = new ConcurrentHashMap<>();
	private static final Map<RegistryKey<World>, Integer> ticksUntilNextCheck = new ConcurrentHashMap<>();

	private MutedNotesDistribution() {}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(MutedNotesDistribution::tick);
		RoundLifecycleHooks.clearWorld(MutedNotesDistribution::reset);
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;

		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean activeGame = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!activeGame && !GexpressTestState.hasModifierTesters()) {
			return;
		}

		WorldModifierComponent modifiers = WorldModifierComponent.KEY.getNullable(world);
		if (modifiers == null) return;

		RegistryKey<World> worldKey = world.getRegistryKey();
		int remaining = ticksUntilNextCheck.getOrDefault(worldKey, 0);
		if (remaining > 0) {
			ticksUntilNextCheck.put(worldKey, remaining - 1);
			return;
		}
		ticksUntilNextCheck.put(worldKey, CHECK_INTERVAL_TICKS - 1);
		Set<UUID> granted = grantedByWorld.computeIfAbsent(worldKey, ignored -> new HashSet<>());

		for (var player : world.getPlayers()) {
			boolean testingMuted = GexpressTestState.isModifierTester(player, MapSelectModifiers.MUTED);
			if (!activeGame && !testingMuted) continue;
			if (!GameFunctions.isPlayerAliveAndSurvival(player) && !testingMuted) continue;
			UUID id = player.getUuid();
			if (!modifiers.isModifier(player, MapSelectModifiers.MUTED)) continue;

			int notes = player.getInventory().count(WatheItems.NOTE);
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

	private static void reset(World world) {
		if (world == null) return;
		grantedByWorld.remove(world.getRegistryKey());
		ticksUntilNextCheck.remove(world.getRegistryKey());
	}
}
