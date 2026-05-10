package dev.mapselect.modifier;

import dev.mapselect.MapSelect;
import dev.mapselect.game.DeadPlayerStatus;
import dev.doctor4t.wathe.api.event.GameEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ModifierDeathStateManager {
	private static final int CHECK_INTERVAL_TICKS = 5;
	private static final Map<UUID, List<Modifier>> suspendedModifiers = new HashMap<>();
	private static int ticks;

	private ModifierDeathStateManager() {}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(ModifierDeathStateManager::tick);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clear());
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear());
	}

	private static void clear() {
		suspendedModifiers.clear();
		ticks = 0;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (++ticks < CHECK_INTERVAL_TICKS) return;
		ticks = 0;

		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(world);
		if (component == null) return;

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (DeadPlayerStatus.isDeadRoundParticipant(player)) {
				suspend(player, component);
			} else if (DeadPlayerStatus.isLivingRoundParticipant(player)) {
				restore(player, component);
			}
		}
		component.sync();
	}

	private static void suspend(ServerPlayerEntity player, WorldModifierComponent component) {
		ArrayList<Modifier> current = component.getModifiers(player.getUuid());
		List<Modifier> stored = suspendedModifiers.computeIfAbsent(player.getUuid(), id -> new ArrayList<>(current));
		mergeMissing(stored, current);
		if (current.isEmpty()) return;
		List<Modifier> removed = new ArrayList<>(current);
		current.clear();
		for (Modifier modifier : removed) {
			safeRemoveModifier(player, modifier);
		}
	}

	private static void restore(ServerPlayerEntity player, WorldModifierComponent component) {
		List<Modifier> stored = suspendedModifiers.remove(player.getUuid());
		if (stored == null) return;
		ArrayList<Modifier> current = component.getModifiers(player.getUuid());
		List<Modifier> oldValues = new ArrayList<>(current);
		current.clear();
		current.addAll(stored);
		for (Modifier modifier : oldValues) {
			if (!contains(stored, modifier)) safeRemoveModifier(player, modifier);
		}
		for (Modifier modifier : stored) {
			if (!contains(oldValues, modifier)) safeAssignModifier(player, modifier);
		}
	}

	private static void mergeMissing(List<Modifier> stored, List<Modifier> current) {
		if (stored == null || current == null || current.isEmpty()) return;
		for (Modifier modifier : current) {
			if (!contains(stored, modifier)) stored.add(modifier);
		}
		for (Iterator<Modifier> iterator = stored.iterator(); iterator.hasNext();) {
			if (iterator.next() == null) iterator.remove();
		}
	}

	private static boolean contains(List<Modifier> modifiers, Modifier needle) {
		if (modifiers == null || needle == null) return false;
		Identifier id = needle.identifier();
		for (Modifier modifier : modifiers) {
			if (modifier == needle) return true;
			if (modifier != null && id != null && id.equals(modifier.identifier())) return true;
		}
		return false;
	}

	private static void safeAssignModifier(ServerPlayerEntity player, Modifier modifier) {
		try {
			ModifierAssigned.EVENT.invoker().assignModifier(player, modifier);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierAssigned listener failed while restoring {} on {}.",
				modifier == null ? "(none)" : modifier.identifier(), player.getName().getString(), t);
		}
	}

	private static void safeRemoveModifier(ServerPlayerEntity player, Modifier modifier) {
		try {
			ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
			player.calculateDimensions();
			player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierRemoved listener failed while suspending {} on {}.",
				modifier == null ? "(none)" : modifier.identifier(), player.getName().getString(), t);
		}
	}
}
