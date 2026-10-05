package io.github.zancrow321.minecraftygo.engine.duel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializer;
import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON encoding of {@link DuelView} for the network. Prompts are tagged with their record name.
 */
public final class ViewCodec {
    private static final Map<String, Class<? extends DuelMessage.Prompt>> PROMPTS = new HashMap<>();

    static {
        for (Class<?> c : DuelMessage.Prompt.class.getPermittedSubclasses()) {
            PROMPTS.put(c.getSimpleName(), c.asSubclass(DuelMessage.Prompt.class));
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(DuelMessage.Prompt.class, (JsonSerializer<DuelMessage.Prompt>) (prompt, type, ctx) -> {
                JsonObject o = new JsonObject();
                o.addProperty("kind", prompt.getClass().getSimpleName());
                o.add("value", ctx.serialize(prompt, prompt.getClass()));
                return o;
            })
            .registerTypeAdapter(DuelMessage.Prompt.class, (JsonDeserializer<DuelMessage.Prompt>) (json, type, ctx) -> {
                JsonObject o = json.getAsJsonObject();
                Class<? extends DuelMessage.Prompt> kind = PROMPTS.get(o.get("kind").getAsString());
                if (kind == null) {
                    throw new JsonParseException("Unknown prompt " + o.get("kind"));
                }
                return ctx.deserialize(o.get("value"), kind);
            })
            .create();

    private ViewCodec() {
    }

    public static String encode(DuelView view) {
        return GSON.toJson(view);
    }

    public static DuelView decode(String json) {
        return GSON.fromJson(json, DuelView.class);
    }
}
