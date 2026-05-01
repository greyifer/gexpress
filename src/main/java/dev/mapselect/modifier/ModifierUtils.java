package dev.mapselect.modifier;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.List;

public final class ModifierUtils {
	private ModifierUtils() {}

	public static boolean has(PlayerEntity player, Modifier modifier) {
		if (player == null || modifier == null || modifier.identifier() == null || player.getWorld() == null) return false;
		return has(player, modifier.identifier());
	}

	public static boolean has(PlayerEntity player, Identifier modifierId) {
		if (player == null || modifierId == null || player.getWorld() == null) return false;
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(player.getWorld());
		if (component == null) return false;
		List<Modifier> modifiers = component.getModifiers(player.getUuid());
		if (modifiers == null || modifiers.isEmpty()) return false;
		for (Modifier modifier : modifiers) {
			if (modifier != null && modifierId.equals(modifier.identifier())) return true;
		}
		return false;
	}
}
