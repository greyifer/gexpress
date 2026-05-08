package dev.mapselect.modifier;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.Modifier;

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
}
