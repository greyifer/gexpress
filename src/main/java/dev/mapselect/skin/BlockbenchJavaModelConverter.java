package dev.mapselect.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class BlockbenchJavaModelConverter {
	private static final Set<String> FACES = Set.of("down", "up", "north", "south", "west", "east");
	private static final Set<String> DISPLAY_CONTEXTS = Set.of(
		"thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand",
		"head", "gui", "ground", "fixed"
	);
	private static final double EPSILON = 0.00001D;

	private BlockbenchJavaModelConverter() {}

	public static JsonObject convert(JsonObject project, JsonObject textureReferences, String primaryTexture)
			throws IOException {
		JsonObject model = new JsonObject();
		if (project.has("credit")) model.add("credit", project.get("credit").deepCopy());
		if (!booleanValue(project, "ambientocclusion", true)) model.addProperty("ambientocclusion", false);
		if (booleanValue(project, "front_gui_light", false)) model.addProperty("gui_light", "front");
		model.add("textures", textureReferences.deepCopy());
		if (primaryTexture != null && !primaryTexture.isBlank() && !textureReferences.has("particle")) {
			model.getAsJsonObject("textures").addProperty("particle", primaryTexture);
		}

		JsonArray sourceElements = array(project, "elements");
		if (sourceElements.isEmpty()) {
			String parent = string(project, "parent", "minecraft:item/handheld");
			model.addProperty("parent", parent.isBlank() ? "minecraft:item/handheld" : parent);
		} else {
			UvResolution defaultResolution = projectResolution(project);
			Map<String, UvResolution> textureResolutions = textureResolutions(project, defaultResolution);
			JsonArray elements = new JsonArray();
			for (JsonElement value : sourceElements) {
				if (!value.isJsonObject()) continue;
				JsonObject source = value.getAsJsonObject();
				if (!booleanValue(source, "export", true)) continue;
				String type = string(source, "type", "cube");
				if (!"cube".equals(type)) throw new IOException("unsupported Blockbench element type: " + type);
				elements.add(convertElement(source, textureResolutions, defaultResolution));
			}
			if (elements.isEmpty()) throw new IOException("model has no exportable cube elements");
			model.add("elements", elements);
		}

		JsonObject display = convertDisplay(object(project, "display"));
		if (!display.isEmpty()) model.add("display", display);
		return model;
	}

	private static JsonObject convertElement(JsonObject source, Map<String, UvResolution> textureResolutions,
			UvResolution defaultResolution) throws IOException {
		JsonArray from = vector(source, "from");
		JsonArray to = vector(source, "to");
		double inflate = number(source, "inflate", 0.0D);
		if (Math.abs(inflate) > EPSILON) {
			from = offset(from, -inflate);
			to = offset(to, inflate);
		}

		JsonObject element = new JsonObject();
		element.add("from", from);
		element.add("to", to);
		if (!booleanValue(source, "shade", true)) element.addProperty("shade", false);
		int lightEmission = (int) number(source, "light_emission", 0.0D);
		if (lightEmission > 0) element.addProperty("light_emission", lightEmission);

		JsonObject rotation = convertRotation(source);
		if (rotation != null) element.add("rotation", rotation);
		JsonObject faces = convertFaces(object(source, "faces"), textureResolutions, defaultResolution);
		if (faces.isEmpty()) throw new IOException("cube element has no textured faces");
		element.add("faces", faces);
		return element;
	}

	private static JsonObject convertRotation(JsonObject source) throws IOException {
		JsonArray values = array(source, "rotation");
		if (values.size() < 3) return null;
		int axisIndex = -1;
		double angle = 0.0D;
		for (int i = 0; i < 3; i++) {
			double current = values.get(i).getAsDouble();
			if (Math.abs(current) <= EPSILON) continue;
			if (axisIndex >= 0) throw new IOException("Minecraft models only support rotation around one axis per cube");
			axisIndex = i;
			angle = current;
		}
		if (axisIndex < 0) return null;
		if (!validAngle(angle)) throw new IOException("unsupported cube rotation angle " + angle);

		JsonObject rotation = new JsonObject();
		rotation.add("origin", source.has("origin") ? vector(source, "origin") : midpoint(vector(source, "from"), vector(source, "to")));
		rotation.addProperty("axis", switch (axisIndex) {
			case 0 -> "x";
			case 1 -> "y";
			default -> "z";
		});
		rotation.addProperty("angle", angle);
		if (booleanValue(source, "rescale", false)) rotation.addProperty("rescale", true);
		return rotation;
	}

	private static JsonObject convertFaces(JsonObject sourceFaces, Map<String, UvResolution> textureResolutions,
			UvResolution defaultResolution) {
		JsonObject faces = new JsonObject();
		for (String direction : FACES) {
			if (!sourceFaces.has(direction) || !sourceFaces.get(direction).isJsonObject()) continue;
			JsonObject source = sourceFaces.getAsJsonObject(direction);
			if (!source.has("texture") || source.get("texture").isJsonNull()) continue;
			String texture = source.get("texture").getAsString();
			String textureKey = texture.replaceFirst("^#", "");
			JsonObject face = new JsonObject();
			if (source.has("uv") && source.get("uv").isJsonArray()) {
				UvResolution resolution = textureResolutions.getOrDefault(textureKey, defaultResolution);
				face.add("uv", normalizedUv(source.getAsJsonArray("uv"), resolution));
			}
			face.addProperty("texture", texture.startsWith("#") || texture.contains(":") ? texture : "#" + texture);
			String cullface = string(source, "cullface", "");
			if (!cullface.isBlank()) face.addProperty("cullface", cullface);
			int rotation = (int) number(source, "rotation", 0.0D);
			if (rotation != 0) face.addProperty("rotation", rotation);
			if (source.has("tintindex") && source.get("tintindex").isJsonPrimitive()) {
				face.addProperty("tintindex", source.get("tintindex").getAsInt());
			}
			faces.add(direction, face);
		}
		return faces;
	}

	private static JsonArray normalizedUv(JsonArray source, UvResolution resolution) {
		JsonArray uv = new JsonArray();
		for (int i = 0; i < Math.min(4, source.size()); i++) {
			double dimension = i % 2 == 0 ? resolution.width() : resolution.height();
			uv.add(source.get(i).getAsDouble() * 16.0D / dimension);
		}
		return uv;
	}

	private static UvResolution projectResolution(JsonObject project) {
		JsonObject resolution = object(project, "resolution");
		return new UvResolution(positive(number(resolution, "width", 16.0D)),
			positive(number(resolution, "height", 16.0D)));
	}

	private static Map<String, UvResolution> textureResolutions(JsonObject project, UvResolution fallback) {
		Map<String, UvResolution> resolutions = new HashMap<>();
		JsonArray textures = array(project, "textures");
		for (int i = 0; i < textures.size(); i++) {
			if (!textures.get(i).isJsonObject()) continue;
			JsonObject texture = textures.get(i).getAsJsonObject();
			UvResolution resolution = new UvResolution(
				positive(number(texture, "uv_width", fallback.width())),
				positive(number(texture, "uv_height", fallback.height())));
			String id = string(texture, "id", Integer.toString(i)).replaceFirst("^#", "");
			resolutions.put(Integer.toString(i), resolution);
			resolutions.put(id, resolution);
		}
		return resolutions;
	}

	private static double positive(double value) {
		return value > EPSILON ? value : 16.0D;
	}

	private static JsonObject convertDisplay(JsonObject source) {
		JsonObject display = new JsonObject();
		for (String context : DISPLAY_CONTEXTS) {
			if (!source.has(context) || !source.get(context).isJsonObject()) continue;
			JsonObject sourceTransform = source.getAsJsonObject(context);
			JsonObject transform = new JsonObject();
			for (String key : new String[] {"rotation", "translation", "scale"}) {
				if (sourceTransform.has(key) && sourceTransform.get(key).isJsonArray()) {
					transform.add(key, sourceTransform.get(key).deepCopy());
				}
			}
			if (!transform.isEmpty()) display.add(context, transform);
		}
		return display;
	}

	private static boolean validAngle(double angle) {
		for (double valid : new double[] {-45.0D, -22.5D, 22.5D, 45.0D}) {
			if (Math.abs(angle - valid) <= EPSILON) return true;
		}
		return false;
	}

	private static JsonArray vector(JsonObject object, String key) throws IOException {
		JsonArray value = array(object, key);
		if (value.size() < 3) throw new IOException("missing or invalid " + key + " vector");
		JsonArray copy = new JsonArray();
		for (int i = 0; i < 3; i++) copy.add(value.get(i).getAsDouble());
		return copy;
	}

	private static JsonArray offset(JsonArray vector, double amount) {
		JsonArray result = new JsonArray();
		for (int i = 0; i < 3; i++) result.add(vector.get(i).getAsDouble() + amount);
		return result;
	}

	private static JsonArray midpoint(JsonArray from, JsonArray to) {
		JsonArray result = new JsonArray();
		for (int i = 0; i < 3; i++) result.add((from.get(i).getAsDouble() + to.get(i).getAsDouble()) / 2.0D);
		return result;
	}

	private static JsonArray array(JsonObject object, String key) {
		return object != null && object.has(key) && object.get(key).isJsonArray()
			? object.getAsJsonArray(key) : new JsonArray();
	}

	private static JsonObject object(JsonObject source, String key) {
		return source != null && source.has(key) && source.get(key).isJsonObject()
			? source.getAsJsonObject(key) : new JsonObject();
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object != null && object.has(key) && object.get(key).isJsonPrimitive()
			? object.get(key).getAsString() : fallback;
	}

	private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
		return object != null && object.has(key) && object.get(key).isJsonPrimitive()
			? object.get(key).getAsBoolean() : fallback;
	}

	private static double number(JsonObject object, String key, double fallback) {
		return object != null && object.has(key) && object.get(key).isJsonPrimitive()
			? object.get(key).getAsDouble() : fallback;
	}

	private record UvResolution(double width, double height) {}
}
