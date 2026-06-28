package dev.mapselect.modifier;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ModifierUtils {
	private ModifierUtils() {}

	public static boolean has(PlayerEntity player, Modifier modifier) {
		if (player == null || modifier == null || modifier.identifier() == null || player.getWorld() == null) return false;
		return has(player, modifier.identifier());
	}

	public static boolean has(PlayerEntity player, Identifier modifierId) {
		if (player == null || modifierId == null || player.getWorld() == null) return false;
		return has(player.getWorld(), player.getUuid(), modifierId);
	}

	public static boolean has(World world, UUID playerId, Identifier modifierId) {
		if (world == null || playerId == null || modifierId == null) return false;
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(world);
		if (component == null) return false;
		List<Modifier> modifiers = component.getModifiers(playerId);
		if (modifiers == null || modifiers.isEmpty()) return false;
		for (Modifier modifier : modifiers) {
			if (modifier != null && modifierId.equals(modifier.identifier())) return true;
		}
		return false;
	}

	public static boolean addIfMissing(ServerPlayerEntity player, Modifier modifier) {
		if (player == null || modifier == null || modifier.identifier() == null || player.getWorld() == null) {
			return false;
		}
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(player.getWorld());
		if (component == null || has(player, modifier.identifier())) return false;
		component.addModifier(player.getUuid(), modifier);
		ModifierAssigned.EVENT.invoker().assignModifier(player, modifier);
		component.sync();
		return true;
	}

	public static boolean addEvenIfPresent(ServerPlayerEntity player, Modifier modifier) {
		if (player == null || modifier == null || modifier.identifier() == null || player.getWorld() == null) {
			return false;
		}
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(player.getWorld());
		if (component == null) return false;
		ArrayList<Modifier> modifiers = component.getModifiers(player.getUuid());
		if (modifiers == null) return false;
		modifiers.add(modifier);
		ModifierAssigned.EVENT.invoker().assignModifier(player, modifier);
		component.sync();
		return true;
	}

	public static boolean removeIfPresent(ServerPlayerEntity player, Modifier modifier) {
		if (player == null || modifier == null || modifier.identifier() == null || player.getWorld() == null) {
			return false;
		}
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(player.getWorld());
		if (component == null) return false;
		ArrayList<Modifier> modifiers = component.getModifiers(player.getUuid());
		if (modifiers == null || modifiers.isEmpty()) return false;
		Identifier id = modifier.identifier();
		boolean removed = modifiers.removeIf(value -> value == modifier
			|| (value != null && id.equals(value.identifier())));
		if (!removed) return false;
		try {
			ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
		} catch (Throwable t) {
			dev.mapselect.MapSelect.LOGGER.warn("ModifierRemoved listener failed while removing {} from {}.",
				modifier.identifier(), player.getName().getString(), t);
		}
		player.calculateDimensions();
		player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
		component.sync();
		return true;
	}
}
