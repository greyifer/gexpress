package dev.mapselect.client.render;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;

public final class GexpressIrisCompat {
	private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
	private static boolean lookedUpPipelineField;
	private static Field worldRendererPipelineField;

	private GexpressIrisCompat() {}

	public static boolean isIrisLoaded() {
		return IRIS_LOADED;
	}

	public static Object capturePipeline(Object worldRenderer) {
		Field field = pipelineField(worldRenderer);
		if (field == null) return null;
		try {
			return field.get(worldRenderer);
		} catch (IllegalAccessException ignored) {
			return null;
		}
	}

	public static void restorePipeline(Object worldRenderer, Object pipeline) {
		Field field = pipelineField(worldRenderer);
		if (field == null) return;
		try {
			field.set(worldRenderer, pipeline);
		} catch (IllegalAccessException ignored) {
			// If Iris changes this field, fall back to vanilla rendering instead of crashing the frame.
		}
	}

	private static Field pipelineField(Object worldRenderer) {
		if (!IRIS_LOADED || worldRenderer == null) return null;
		if (!lookedUpPipelineField) {
			lookedUpPipelineField = true;
			Class<?> type = worldRenderer.getClass();
			while (type != null) {
				try {
					Field field = type.getDeclaredField("pipeline");
					field.setAccessible(true);
					worldRendererPipelineField = field;
					break;
				} catch (NoSuchFieldException ignored) {
					type = type.getSuperclass();
				}
			}
		}
		return worldRendererPipelineField;
	}
}
