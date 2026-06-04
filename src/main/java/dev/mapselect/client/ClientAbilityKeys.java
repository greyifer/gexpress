package dev.mapselect.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

public final class ClientAbilityKeys {
	private static KeyBinding primaryBinding;
	private static KeyBinding secondaryBinding;
	private static KeyBinding guidebookBinding;
	private static KeyBinding guidebookTabBinding;
	private static KeyBinding spectatorVoicePreviousBinding;
	private static KeyBinding spectatorVoiceNextBinding;
	private static Field boundKeyField;
	private static boolean lookedUpBoundKeyField;

	private ClientAbilityKeys() {}

	public static void register() {
		if (secondaryBinding != null) return;
		primaryBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.gexpress.primary_ability",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_R,
			"category.gexpress"
		));
		secondaryBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.gexpress.secondary_ability",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_B,
			"category.gexpress"
		));
		guidebookBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.gexpress.guidebook",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_G,
			"category.gexpress"
		));
		guidebookTabBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.gexpress.guidebook_tab",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_TAB,
			"category.gexpress"
		));
		spectatorVoicePreviousBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.gexpress.spectator_voice_previous",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_LEFT_BRACKET,
			"category.gexpress"
		));
		spectatorVoiceNextBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.gexpress.spectator_voice_next",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_BRACKET,
			"category.gexpress"
		));
	}

	public static KeyBinding primaryBinding() {
		return primaryBinding;
	}

	public static KeyBinding secondaryBinding() {
		return secondaryBinding;
	}

	public static KeyBinding guidebookBinding() {
		return guidebookBinding;
	}

	public static KeyBinding guidebookTabBinding() {
		return guidebookTabBinding;
	}

	public static KeyBinding spectatorVoicePreviousBinding() {
		return spectatorVoicePreviousBinding;
	}

	public static KeyBinding spectatorVoiceNextBinding() {
		return spectatorVoiceNextBinding;
	}

	public static boolean matches(KeyBinding binding, int keyCode, int scanCode) {
		if (binding == null) return false;
		InputUtil.Key key = boundKey(binding);
		if (key == null) return keyCode == GLFW.GLFW_KEY_TAB && binding == guidebookTabBinding;
		InputUtil.Type type = key.getCategory();
		if (type == InputUtil.Type.KEYSYM) return key.getCode() == keyCode;
		if (type == InputUtil.Type.SCANCODE) return key.getCode() == scanCode;
		return false;
	}

	public static boolean isDown(MinecraftClient client, KeyBinding binding) {
		boolean down = isPhysicallyDown(client, binding);
		if (ClientCopycatState.shouldSuppressBorrowedSecondary(client, binding, down)) return false;
		return down;
	}

	private static boolean isPhysicallyDown(MinecraftClient client, KeyBinding binding) {
		if (binding == null) return false;
		InputUtil.Key key = boundKey(binding);
		if (key == null || client == null || client.getWindow() == null) return binding.isPressed();

		long handle = client.getWindow().getHandle();
		int code = key.getCode();
		InputUtil.Type type = key.getCategory();
		if (type == InputUtil.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(handle, code) == GLFW.GLFW_PRESS;
		}
		if (type == InputUtil.Type.KEYSYM) {
			return GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
		}
		if (type == InputUtil.Type.SCANCODE) {
			return binding.isPressed();
		}
		return binding.isPressed();
	}

	public static String displayName(KeyBinding binding) {
		if (binding == null) return "";
		try {
			return binding.getBoundKeyLocalizedText().getString();
		} catch (Throwable ignored) {
			return "";
		}
	}

	private static InputUtil.Key boundKey(KeyBinding binding) {
		try {
			if (!lookedUpBoundKeyField) {
				boundKeyField = KeyBinding.class.getDeclaredField("boundKey");
				boundKeyField.setAccessible(true);
				lookedUpBoundKeyField = true;
			}
			Object value = boundKeyField == null ? null : boundKeyField.get(binding);
			return value instanceof InputUtil.Key key ? key : null;
		} catch (Throwable ignored) {
			lookedUpBoundKeyField = true;
			boundKeyField = null;
			return null;
		}
	}
}
