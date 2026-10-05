package io.github.zancrow321.minecraftygo.tools.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** JSON helpers: streaming over huge top-level arrays and stable pretty printing. */
public final class Json {
	public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	public static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

	private Json() {}

	/** Calls {@code consumer} for each object of a top-level JSON array without loading the whole file. */
	public static void forEachObject(Path file, Consumer<JsonObject> consumer) {
		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8); JsonReader reader = new JsonReader(in)) {
			reader.beginArray();
			while (reader.hasNext()) {
				consumer.accept(JsonParser.parseReader(reader).getAsJsonObject());
			}
			reader.endArray();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read " + file, e);
		}
	}

	public static JsonElement read(Path file) {
		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(in);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read " + file, e);
		}
	}

	public static void write(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write " + file, e);
		}
	}

	public static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value == null || value.isJsonNull() ? null : value.getAsString();
	}
}
