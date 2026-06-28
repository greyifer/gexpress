package dev.mapselect.client.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.mapselect.skin.BlockbenchJavaModelConverter;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockbenchJavaModelConverterTest {
	@Test
	void convertsBlockbenchCubeIntoMinecraftModelShape() throws Exception {
		JsonObject project = JsonParser.parseString("""
			{
			  "front_gui_light": true,
			  "elements": [{
			    "type": "cube", "export": true, "from": [7, 1, 9], "to": [8, 2, 10],
			    "inflate": 0.01, "rotation": [-22.5, 0, 0], "origin": [7.5, 1.5, 9.5],
			    "faces": {"north": {"uv": [0, 0, 1, 1], "texture": 0}}
			  }],
			  "display": {
			    "gui": {"rotation": [90, 0, 0], "translation": [0, 1, 0], "scale": [1, 1, 1]},
			    "on_shelf": {"rotation": [0, 90, 0]}
			  }
			}
			""").getAsJsonObject();
		JsonObject textures = new JsonObject();
		textures.addProperty("0", "gexpress:item/custom_skins/test");

		JsonObject model = BlockbenchJavaModelConverter.convert(project, textures,
			"gexpress:item/custom_skins/test");
		JsonObject element = model.getAsJsonArray("elements").get(0).getAsJsonObject();

		assertEquals(6.99D, element.getAsJsonArray("from").get(0).getAsDouble(), 0.00001D);
		assertEquals(8.01D, element.getAsJsonArray("to").get(0).getAsDouble(), 0.00001D);
		assertTrue(element.get("rotation").isJsonObject());
		assertEquals("x", element.getAsJsonObject("rotation").get("axis").getAsString());
		assertEquals(-22.5D, element.getAsJsonObject("rotation").get("angle").getAsDouble());
		assertEquals("#0", element.getAsJsonObject("faces").getAsJsonObject("north")
			.get("texture").getAsString());
		assertEquals("front", model.get("gui_light").getAsString());
		assertTrue(model.getAsJsonObject("display").has("gui"));
		assertFalse(model.getAsJsonObject("display").has("on_shelf"));
	}

	@Test
	void rejectsCubeRotatedAroundMultipleAxes() {
		JsonObject project = JsonParser.parseString("""
			{"elements": [{"type": "cube", "from": [0,0,0], "to": [1,1,1],
			"rotation": [22.5,22.5,0], "origin": [0,0,0],
			"faces": {"north": {"texture": 0}}}]}
			""").getAsJsonObject();
		JsonObject textures = new JsonObject();
		textures.addProperty("0", "gexpress:item/custom_skins/test");

		IOException error = assertThrows(IOException.class, () ->
			BlockbenchJavaModelConverter.convert(project, textures, "gexpress:item/custom_skins/test"));
		assertTrue(error.getMessage().contains("one axis"));
	}

	@Test
	void normalizesBlockbenchUvResolutionToMinecraftSpace() throws Exception {
		JsonObject project = JsonParser.parseString("""
			{
			  "resolution": {"width": 32, "height": 32},
			  "textures": [{"id": "0", "uv_width": 32, "uv_height": 32}],
			  "elements": [{"type": "cube", "from": [0,0,0], "to": [1,1,1],
			    "faces": {"north": {"uv": [8, 12, 24, 28], "texture": 0}}}]
			}
			""").getAsJsonObject();
		JsonObject textures = new JsonObject();
		textures.addProperty("0", "gexpress:item/custom_skins/test");

		JsonArray uv = BlockbenchJavaModelConverter.convert(project, textures, "")
			.getAsJsonArray("elements").get(0).getAsJsonObject()
			.getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv");
		assertEquals(4.0D, uv.get(0).getAsDouble());
		assertEquals(6.0D, uv.get(1).getAsDouble());
		assertEquals(12.0D, uv.get(2).getAsDouble());
		assertEquals(14.0D, uv.get(3).getAsDouble());
	}
}
