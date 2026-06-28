package dev.mapselect.client.input;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.role.copycat.ClientCopycatState;


import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.mixin.client.KeyBindingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;

import java.util.Set;

public final class ClientExternalAbilityKeys {
	private static final Set<String> EXTERNAL_ABILITY_BINDINGS = Set.of(
		"key.kinswathe.ability",
		"key.noellesroles.ability",
		"key.starexpress.ability"
	);
	private static final String USE_KEY = "key.use";
	private static boolean wasPrimaryDown;
	private static boolean consumedPrimaryPress;

	private ClientExternalAbilityKeys() {}

	public static Boolean isPressed(KeyBinding binding) {
		BindingMode mode = bindingMode(binding);
		if (mode == BindingMode.NONE) return null;
		MinecraftClient client = MinecraftClient.getInstance();
		if (!canBridge(client, mode)) return mode == BindingMode.EXTERNAL_ABILITY ? false : null;

		boolean primaryDown = primaryDown(client);
		if (mode == BindingMode.VANILLA_USE) {
			return rawPressed(binding) || primaryDown;
		}
		return primaryDown;
	}

	public static Boolean wasPressed(KeyBinding binding) {
		BindingMode mode = bindingMode(binding);
		if (mode == BindingMode.NONE) return null;
		MinecraftClient client = MinecraftClient.getInstance();
		if (!canBridge(client, mode)) {
			trackPrimaryDown(client);
			if (mode == BindingMode.EXTERNAL_ABILITY) {
				clearQueuedPresses(binding);
				return false;
			}
			return null;
		}

		boolean primaryPressed = primaryWasPressed(client);
		if (mode == BindingMode.VANILLA_USE) {
			return primaryPressed ? true : null;
		}
		clearQueuedPresses(binding);
		return primaryPressed;
	}

	private static BindingMode bindingMode(KeyBinding binding) {
		String key = translationKey(binding);
		if (EXTERNAL_ABILITY_BINDINGS.contains(key)) return BindingMode.EXTERNAL_ABILITY;
		if (USE_KEY.equals(key) && isStupidExpressInteractionRole(MinecraftClient.getInstance())) {
			return BindingMode.VANILLA_USE;
		}
		return BindingMode.NONE;
	}

	private static boolean canBridge(MinecraftClient client, BindingMode mode) {
		if (client == null || client.player == null || client.world == null || client.currentScreen != null) {
			return false;
		}
		if (!ClientRoleRevealState.canUseRoleAbility(client)) return false;
		return mode != BindingMode.VANILLA_USE || isStupidExpressInteractionRole(client);
	}

	private static boolean isStupidExpressInteractionRole(MinecraftClient client) {
		Identifier roleId = localRoleId(client);
		return roleId != null && "stupid_express".equals(roleId.getNamespace())
			&& ("amnesiac".equals(roleId.getPath()) || "thief".equals(roleId.getPath())
			|| "necromancer".equals(roleId.getPath()));
	}

	private static Identifier localRoleId(MinecraftClient client) {
		try {
			if (client == null || client.player == null || client.world == null) return null;
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			Identifier actualRole = role == null ? null : role.identifier();
			return ClientCopycatState.effectiveRoleId(client, actualRole);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static boolean primaryDown(MinecraftClient client) {
		return ClientAbilityKeys.isDown(client, ClientAbilityKeys.primaryBinding());
	}

	private static boolean primaryWasPressed(MinecraftClient client) {
		boolean down = primaryDown(client);
		boolean pressed = down && !wasPrimaryDown && !consumedPrimaryPress;
		wasPrimaryDown = down;
		if (!down) consumedPrimaryPress = false;
		if (pressed) consumedPrimaryPress = true;
		return pressed;
	}

	private static void trackPrimaryDown(MinecraftClient client) {
		boolean down = client != null && primaryDown(client);
		wasPrimaryDown = down;
		if (!down) consumedPrimaryPress = false;
	}

	private static String translationKey(KeyBinding binding) {
		try {
			return binding == null ? "" : ((KeyBindingAccessor) binding).gexpress$translationKey();
		} catch (Throwable ignored) {
			return "";
		}
	}

	private static boolean rawPressed(KeyBinding binding) {
		try {
			return binding != null && ((KeyBindingAccessor) binding).gexpress$pressed();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void clearQueuedPresses(KeyBinding binding) {
		try {
			if (binding != null) ((KeyBindingAccessor) binding).gexpress$timesPressed(0);
		} catch (Throwable ignored) {
		}
	}

	private enum BindingMode {
		NONE,
		EXTERNAL_ABILITY,
		VANILLA_USE
	}
}
