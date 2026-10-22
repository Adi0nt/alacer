package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonWriterTest {

	@Test
	void writesNestedObjects() {
		Map<String, String> env = new LinkedHashMap<>();
		env.put("k", "v");
		String json = new JsonWriter().beginObject()
			.field("name", "a\"b\\c\n")
			.field("count", 3L)
			.field("ratio", 0.5)
			.field("nan", Double.NaN)
			.field("ok", true)
			.fields("env", env)
			.beginObject("empty").endObject()
			.endObject().toString();
		String expected = "{\n"
			+ "  \"name\": \"a\\\"b\\\\c\\n\",\n"
			+ "  \"count\": 3,\n"
			+ "  \"ratio\": 0.5000,\n"
			+ "  \"nan\": null,\n"
			+ "  \"ok\": true,\n"
			+ "  \"env\": {\n"
			+ "    \"k\": \"v\"\n"
			+ "  },\n"
			+ "  \"empty\": {}\n"
			+ "}";
		assertEquals(expected, json);
	}

	@Test
	void escapesControlCharacters() {
		String json = new JsonWriter().beginObject().field("c", "\u0001").endObject().toString();
		assertEquals("{\n  \"c\": \"\\u0001\"\n}", json);
	}
}
