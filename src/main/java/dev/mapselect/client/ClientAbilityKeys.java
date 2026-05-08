package dev.mapselect.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

public final class ClientAbilityKeys {
	private static KeyBinding secondaryBinding;
	private static KeyBinding guidebookBinding;
	private static KeyBinding guidebookTabBinding;
	private static Field boundKeyField;
	private static boolean lookedUpBoundKeyField;

	private ClientAbilityKeys() {}

	public static void register() {
		if (secondaryBinding != null) return;
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
	}

	public static KeyBinding primaryBinding() {
		KeyBinding binding = staticKeyBinding("org.BsXinQin.kinswathe.client.KinsWatheInitializeClient", "abilityBind");
		if (binding != null) return binding;
		return staticKeyBinding("org.agmas.noellesroles.client.NoellesrolesClient", "abilityBind");
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
		if (binding == null) return false;
		InputUtil.Key key = boundKey(binding);
		if (key == null || client == null || client.getWindow() == null) return binding.isPressed();

		long handle = client.getWindow().getHandle();
		int code = key.getCode();
		InputUtil.Type type = key.getCategory();
		if (type == InputUtil.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(handle, code) == GLFW.GLFW_PRESS;
		}
		if (type == InputUtil.Type.KEYSYM || type == InputUtil.Type.SCANCODE) {
			return InputUtil.isKeyPressed(handle, code);
		}
		return binding.isPressed();
	}

	private static KeyBinding staticKeyBinding(String className, String fieldName) {
		try {
			Class<?> cls = Class.forName(className);
			Field field = cls.getDeclaredField(fieldName);
			field.setAccessible(true);
			Object value = field.get(null);
			return value instanceof KeyBinding binding ? binding : null;
		} catch (Throwable ignored) {
			return null;
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
