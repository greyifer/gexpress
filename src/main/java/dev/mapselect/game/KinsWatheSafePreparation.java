package dev.mapselect.game;

import dev.mapselect.MapSelect;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.lang.reflect.Method;

/** Reads KinsWathe's synced safe-preparation component without making it a hard dependency. */
public final class KinsWatheSafePreparation {
	private static final String MOD_ID = "kinswathe";
	private static final String COMPONENT_CLASS = "org.BsXinQin.kinswathe.component.GameSafeComponent";
	private static volatile Accessor accessor;
	private static volatile boolean resolved;
	private static volatile boolean readFailureLogged;

	private KinsWatheSafePreparation() {}

	public static boolean isActive(World world) {
		if (world == null || !FabricLoader.getInstance().isModLoaded(MOD_ID)) return false;
		Accessor current = accessor();
		if (current == null) return false;
		try {
			Object component = current.key().getNullable(world);
			return component != null && Boolean.TRUE.equals(current.isSafe().invoke(component));
		} catch (ReflectiveOperationException | LinkageError error) {
			if (!readFailureLogged) {
				readFailureLogged = true;
				MapSelect.LOGGER.warn("Could not read KinsWathe's safe-preparation state.", error);
			}
			return false;
		}
	}

	private static Accessor accessor() {
		if (resolved) return accessor;
		synchronized (KinsWatheSafePreparation.class) {
			if (resolved) return accessor;
			try {
				Class<?> componentClass = Class.forName(COMPONENT_CLASS);
				Object key = componentClass.getField("KEY").get(null);
				if (key instanceof ComponentKey<?> componentKey) {
					Method isSafe = componentClass.getMethod("isSafe");
					accessor = new Accessor(componentKey, isSafe);
				}
			} catch (ReflectiveOperationException | LinkageError error) {
				MapSelect.LOGGER.warn("KinsWathe is loaded but its safe-preparation component is unavailable.", error);
			} finally {
				resolved = true;
			}
			return accessor;
		}
	}

	private record Accessor(ComponentKey<?> key, Method isSafe) {}
}
