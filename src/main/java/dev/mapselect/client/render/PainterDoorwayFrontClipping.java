package dev.mapselect.client.render;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderStage;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class PainterDoorwayFrontClipping {
	private static final Pattern MAIN_PATTERN = Pattern.compile("void\\s+main\\s*\\(\\s*\\)\\s*\\{");
	private static final Set<String> TERRAIN_SHADERS = Set.of(
		"rendertype_solid",
		"rendertype_cutout",
		"rendertype_cutout_mipped",
		"rendertype_translucent"
	);
	private static final Set<String> ENTITY_SHADERS = Set.of(
		"rendertype_entity_solid",
		"rendertype_entity_cutout",
		"rendertype_entity_cutout_no_cull",
		"rendertype_entity_cutout_no_cull_z_offset",
		"rendertype_item_entity_translucent_cull",
		"rendertype_entity_translucent_cull",
		"rendertype_entity_translucent",
		"rendertype_entity_smooth_cutout",
		"rendertype_beacon_beam",
		"rendertype_entity_translucent_emissive",
		"portal_area",
		"particle"
	);

	private static boolean clippingEnabled;
	private static float clipX;
	private static float clipY;
	private static float clipZ;
	private static float clipW = 1.0F;
	private static float modelClipX;
	private static float modelClipY;
	private static float modelClipZ;
	private static float modelClipW = 1.0F;

	private PainterDoorwayFrontClipping() {}

	public static boolean shouldAddUniform(String shaderName) {
		String normalized = normalizeShaderName(shaderName);
		return TERRAIN_SHADERS.contains(normalized) || ENTITY_SHADERS.contains(normalized);
	}

	public static List<String> transformShader(ShaderStage.Type type, String shaderName, List<String> source) {
		if (type != ShaderStage.Type.VERTEX || source == null || source.isEmpty()) return source;
		String normalized = normalizeShaderName(shaderName);
		boolean terrain = TERRAIN_SHADERS.contains(normalized);
		boolean entity = ENTITY_SHADERS.contains(normalized);
		if (!terrain && !entity) return source;

		String joined = String.join("\n", source);
		if (joined.contains("iportal_ClippingEquation")) return source;
		String body = terrain
			? "uniform vec4 iportal_ClippingEquation;\n"
				+ "void main() {\n"
				+ "    gl_ClipDistance[0] = dot(Position.xyz + ChunkOffset, iportal_ClippingEquation.xyz)\n"
				+ "        + iportal_ClippingEquation.w;"
			: "uniform vec4 iportal_ClippingEquation;\n"
				+ "void main() {\n"
				+ "    gl_ClipDistance[0] = dot(Position.xyz, iportal_ClippingEquation.xyz)\n"
				+ "        + iportal_ClippingEquation.w;";
		String transformed = MAIN_PATTERN.matcher(joined).replaceFirst(body);
		return List.of(transformed);
	}

	public static void setup(Vec3d planePoint, Direction nonClippedSide, Vec3d cameraPos) {
		setup(planePoint, nonClippedSide, cameraPos, null);
	}

	public static void setup(Vec3d planePoint, Direction nonClippedSide, Vec3d cameraPos, Matrix4f modelView) {
		if (planePoint == null || nonClippedSide == null || cameraPos == null) {
			disable();
			return;
		}
		Vec3d normal = Vec3d.of(nonClippedSide.getVector()).normalize();
		Vec3d relativePlanePoint = planePoint.add(normal.multiply(0.015D)).subtract(cameraPos);
		clipX = (float) normal.x;
		clipY = (float) normal.y;
		clipZ = (float) normal.z;
		clipW = (float) -normal.dotProduct(relativePlanePoint);
		updateModelViewEquation(modelView);
		clippingEnabled = true;
		GL11.glEnable(GL11.GL_CLIP_PLANE0);
	}

	public static void disable() {
		if (clippingEnabled) {
			GL11.glDisable(GL11.GL_CLIP_PLANE0);
		}
		clippingEnabled = false;
		clipX = 0.0F;
		clipY = 0.0F;
		clipZ = 0.0F;
		clipW = 1.0F;
		modelClipX = 0.0F;
		modelClipY = 0.0F;
		modelClipZ = 0.0F;
		modelClipW = 1.0F;
	}

	public static void updateShader(ShaderProgram shader) {
		if (shader == null) return;
		GlUniform uniform = shader.getUniform("iportal_ClippingEquation");
		if (uniform == null) return;
		if (clippingEnabled) {
			uniform.set(clipX, clipY, clipZ, clipW);
		} else {
			uniform.set(0.0F, 0.0F, 0.0F, 1.0F);
		}
	}

	public static void updateSodiumUniform(net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat4v uniform) {
		if (uniform == null) return;
		if (clippingEnabled) {
			uniform.set(new float[]{modelClipX, modelClipY, modelClipZ, modelClipW});
		} else {
			uniform.set(new float[]{0.0F, 0.0F, 0.0F, 1.0F});
		}
	}

	public static String transformSodiumShader(String shaderName, String source) {
		if (source == null || source.contains("iportal_ClippingEquation")) return source;
		String normalized = shaderName == null ? "" : shaderName.replace('\\', '/');
		if (!normalized.equals("sodium:blocks/block_layer_opaque.vsh")) return source;
		return transformModelViewShader(source,
			"(u_ModelViewMatrix * vec4(position, 1.0)).xyz");
	}

	public static String transformIrisShader(String shaderName, String source) {
		if (source == null || source.contains("iportal_ClippingEquation")) return source;
		String normalized = shaderName == null ? "" : shaderName;
		if (!normalized.equals("iris_gbuffers_terrain") && !normalized.equals("iris_gbuffers_water")) {
			return source;
		}
		return transformModelViewShader(source,
			"(iris_ModelViewMatrix * getVertexPosition()).xyz");
	}

	public static String transformIrisPatchedShader(String source) {
		if (source == null || source.contains("iportal_ClippingEquation")) return source;
		if (!source.contains("getVertexPosition()") || !source.contains("iris_ModelViewMatrix")) {
			return source;
		}
		return transformModelViewShader(source,
			"(iris_ModelViewMatrix * getVertexPosition()).xyz");
	}

	private static String normalizeShaderName(String shaderName) {
		if (shaderName == null) return "";
		String normalized = shaderName.replace('\\', '/');
		int slash = normalized.lastIndexOf('/');
		if (slash >= 0) normalized = normalized.substring(slash + 1);
		if (normalized.endsWith(".vsh") || normalized.endsWith(".fsh") || normalized.endsWith(".json")) {
			normalized = normalized.substring(0, normalized.length() - 4);
		}
		return normalized;
	}

	private static String transformModelViewShader(String source, String positionExpression) {
		String withUniform = MAIN_PATTERN.matcher(source).replaceFirst(
			"uniform vec4 iportal_ClippingEquation;\nvoid main() {"
		);
		return withUniform.replaceFirst("\\}(?![\\s\\S]*\\})",
			"    gl_ClipDistance[0] = dot(" + positionExpression
				+ ", iportal_ClippingEquation.xyz) + iportal_ClippingEquation.w;\n}");
	}

	private static void updateModelViewEquation(Matrix4f modelView) {
		if (modelView == null) {
			modelClipX = clipX;
			modelClipY = clipY;
			modelClipZ = clipZ;
			modelClipW = clipW;
			return;
		}
		Vector4f equation = new Vector4f(clipX, clipY, clipZ, clipW);
		new Matrix4f(modelView).invert().transpose().transform(equation);
		modelClipX = equation.x();
		modelClipY = equation.y();
		modelClipZ = equation.z();
		modelClipW = equation.w();
	}
}
