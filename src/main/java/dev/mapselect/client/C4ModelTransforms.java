package dev.mapselect.client;

import dev.mapselect.role.bombspecialist.C4PlacementPreset;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

public final class C4ModelTransforms {
	private C4ModelTransforms() {}

	public static void rotateToSurface(MatrixStack matrices, float yaw, float pitch) {
		rotateIfNeeded(matrices, RotationAxis.POSITIVE_Y, yaw);
		rotateIfNeeded(matrices, RotationAxis.POSITIVE_X, pitch);
	}

	public static void applyPlacement(MatrixStack matrices, C4PlacementPreset preset) {
		if (preset == null) preset = C4PlacementPreset.DEFAULT;
		matrices.translate(preset.offsetX(), preset.offsetY(), preset.offsetZ());
		rotateIfNeeded(matrices, RotationAxis.POSITIVE_X, preset.rotationX());
		rotateIfNeeded(matrices, RotationAxis.POSITIVE_Y, preset.rotationY());
		rotateIfNeeded(matrices, RotationAxis.POSITIVE_Z, preset.rotationZ());
		slantIfNeeded(matrices, preset.slant());
		float scale = preset.scale();
		matrices.scale(scale, scale, scale);
	}

	public static void applySurfacePlacement(MatrixStack matrices, C4PlacementPreset preset) {
		if (preset == null) preset = C4PlacementPreset.DEFAULT;
		float surfaceZ = preset.offsetZ() - C4PlacementPreset.DEFAULT.offsetZ() - 0.11F;
		matrices.translate(preset.offsetX(), preset.offsetY(), surfaceZ);
		rotateIfNeeded(matrices, RotationAxis.POSITIVE_Z, preset.rotationZ());
		float scale = preset.scale();
		matrices.scale(scale, scale, scale);
	}

	private static void rotateIfNeeded(MatrixStack matrices, RotationAxis axis, float degrees) {
		if (degrees != 0.0F) {
			matrices.multiply(axis.rotationDegrees(degrees));
		}
	}

	private static void slantIfNeeded(MatrixStack matrices, float degrees) {
		if (degrees != 0.0F) {
			matrices.multiply(new Quaternionf().rotationAxis((float) Math.toRadians(degrees),
				0.70710677F, 0.0F, 0.70710677F));
		}
	}
}
