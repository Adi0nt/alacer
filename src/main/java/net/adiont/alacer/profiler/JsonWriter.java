package net.adiont.alacer.profiler;

import java.util.Locale;
import java.util.Map;

/**
 * Minimal streaming JSON writer for profile reports. Only what the reports need:
 * nested objects, strings, numbers and booleans, with two-space indentation.
 */
public final class JsonWriter {

	private final StringBuilder out = new StringBuilder();
	private int depth;
	private boolean first = true;

	public JsonWriter beginObject() {
		this.out.append('{');
		this.depth++;
		this.first = true;
		return this;
	}

	public JsonWriter beginObject(String name) {
		key(name);
		return beginObject();
	}

	public JsonWriter endObject() {
		this.depth--;
		if (!this.first) {
			newline();
		}
		this.out.append('}');
		this.first = false;
		return this;
	}

	public JsonWriter field(String name, String value) {
		key(name);
		string(value);
		return this;
	}

	public JsonWriter field(String name, long value) {
		key(name);
		this.out.append(value);
		return this;
	}

	public JsonWriter field(String name, boolean value) {
		key(name);
		this.out.append(value);
		return this;
	}

	public JsonWriter field(String name, double value) {
		key(name);
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			this.out.append("null");
		} else {
			this.out.append(String.format(Locale.ROOT, "%.4f", value));
		}
		return this;
	}

	public JsonWriter fields(String name, Map<String, String> values) {
		beginObject(name);
		for (Map.Entry<String, String> e : values.entrySet()) {
			field(e.getKey(), e.getValue());
		}
		return endObject();
	}

	private void key(String name) {
		if (!this.first) {
			this.out.append(',');
		}
		this.first = false;
		newline();
		string(name);
		this.out.append(": ");
	}

	private void newline() {
		this.out.append('\n');
		for (int i = 0; i < this.depth; i++) {
			this.out.append("  ");
		}
	}

	private void string(String value) {
		if (value == null) {
			this.out.append("null");
			return;
		}
		this.out.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"':
					this.out.append("\\\"");
					break;
				case '\\':
					this.out.append("\\\\");
					break;
				case '\n':
					this.out.append("\\n");
					break;
				case '\r':
					this.out.append("\\r");
					break;
				case '\t':
					this.out.append("\\t");
					break;
				default:
					if (c < 0x20) {
						this.out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
					} else {
						this.out.append(c);
					}
			}
		}
		this.out.append('"');
	}

	@Override
	public String toString() {
		return this.out.toString();
	}
}
