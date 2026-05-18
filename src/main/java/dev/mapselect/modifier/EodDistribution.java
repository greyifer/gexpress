package dev.mapselect.modifier;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.MapSelect;
import dev.mapselect.registry.MapSelectItems;
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

/**
 * Hands out Pliers to every player wearing the EOD Specialist modifier once per game, as
 * soon as Wathe's {@link GameWorldComponent.GameStatus#ACTIVE} phase begins.
 *
 * <h2>Why the single-dimension + inventory-count guard</h2>
 * {@link ServerTickEvents#END_WORLD_TICK} fires once per loaded dimension each tick. An
 * earlier version tracked {@code wasActive} as a single static — which meant the nether's
 * tick (with {@code GameStatus != ACTIVE} in that world) would clear the {@code granted}
 * set every tick, and the next overworld tick would re-grant Pliers. The fix is twofold:
 * <ol>
 *   <li>Gate the whole handler on {@link World#OVERWORLD} so only the game-hosting world
 *       ticks ever touch our state.</li>
 *   <li>Additionally check {@code inventory.count(PLIERS) > 0} before granting — if a
 *       player somehow already has one (picked back up after dropping, re-grant raced a
 *       state wipe, etc.) we don't layer a second.</li>
 * </ol>
 * The UUID dedup set is kept as the fast path; the inventory check is the fallback.
 *
 * <p>Runs purely server-side — the client is never trusted with inventory insertion.
 */
public final class EodDistribution {
	private EodDistribution() {}

	/** UUIDs that received their Pliers this game. Cleared whenever the game isn't ACTIVE. */
	private static final Set<UUID> granted = new HashSet<>();
	private static final int CHECK_INTERVAL_TICKS = 10;
	private static int ticksUntilNextCheck = 0;

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(EodDistribution::tick);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> reset());
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> reset());
	}

	private static void tick(ServerWorld world) {
		// Wathe games only run in the overworld; ignoring other dimensions keeps our single
		// static state from being thrashed by non-game worlds each tick.
		if (world.getRegistryKey() != World.OVERWORLD) return;

		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean activeGame = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!activeGame && !GexpressTestState.hasModifierTesters()) {
			return;
		}

		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(world);
		if (mods == null) return;

		if (ticksUntilNextCheck > 0) {
			ticksUntilNextCheck--;
			return;
		}
		ticksUntilNextCheck = CHECK_INTERVAL_TICKS - 1;

		for (var player : world.getPlayers()) {
			boolean testingEod = GexpressTestState.isModifierTester(player, MapSelectModifiers.EOD_SPECIALIST);
			if (!activeGame && !testingEod) continue;
			if (!GameFunctions.isPlayerAliveAndSurvival(player) && !testingEod) continue;
			UUID id = player.getUuid();
			if (!mods.isModifier(player, MapSelectModifiers.EOD_SPECIALIST)) continue;

			int pliers = player.getInventory().count(MapSelectItems.PLIERS);
			if (pliers > 1) trimPliersToOne(player);
			if (pliers > 0) {
				granted.add(id);
				continue;
			}
			if (granted.contains(id)) continue;

			ItemStack stack = new ItemStack(MapSelectItems.PLIERS);
			if (!player.getInventory().insertStack(stack)) {
				MapSelect.LOGGER.debug("Skipped EOD Pliers grant for {}; inventory is full",
					player.getName().getString());
				continue;
			}
			granted.add(id);
			MapSelect.LOGGER.debug("Handed Pliers to EOD Specialist {}", player.getName().getString());
		}
	}

	private static void trimPliersToOne(net.minecraft.server.network.ServerPlayerEntity player) {
		int kept = 0;
		boolean changed = false;
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (stack.isEmpty() || !stack.isOf(MapSelectItems.PLIERS)) continue;
			if (kept > 0) {
				player.getInventory().setStack(slot, ItemStack.EMPTY);
				changed = true;
				continue;
			}
			kept = 1;
			if (stack.getCount() > 1) {
				stack.setCount(1);
				changed = true;
			}
		}
		if (changed) player.playerScreenHandler.syncState();
	}

	private static void reset() {
		granted.clear();
		ticksUntilNextCheck = 0;
	}
}
