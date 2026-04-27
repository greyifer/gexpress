package dev.mapselect.modifier;

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
	}

	private static void tick(ServerWorld world) {
		// Wathe games only run in the overworld; ignoring other dimensions keeps our single
		// static state from being thrashed by non-game worlds each tick.
		if (world.getRegistryKey() != World.OVERWORLD) return;

		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean activeGame = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!activeGame && !GexpressTestState.hasModifierTesters()) {
			// Game not running — flush memory so the next match starts fresh.
			if (!granted.isEmpty()) granted.clear();
			ticksUntilNextCheck = 0;
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
			if (granted.contains(id)) continue;
			if (!mods.isModifier(player, MapSelectModifiers.EOD_SPECIALIST)) continue;

			// Belt-and-suspenders: if the player already has Pliers (e.g. from a prior grant
			// where our UUID memory got wiped), don't stack another one. Record the grant so
			// we skip the inventory scan on future ticks.
			if (player.getInventory().count(MapSelectItems.PLIERS) > 0) {
				granted.add(id);
				continue;
			}

			ItemStack stack = new ItemStack(MapSelectItems.PLIERS);
			if (!player.getInventory().insertStack(stack)) {
				// Inventory full — drop at feet rather than losing the tool silently.
				player.dropItem(stack, false);
			}
			granted.add(id);
			MapSelect.LOGGER.info("Handed Pliers to EOD Specialist {}", player.getName().getString());
		}
	}
}
