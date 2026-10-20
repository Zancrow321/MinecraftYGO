package io.github.zancrow321.jadm.quest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import io.github.zancrow321.jadm.Jadm;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every quest a server has, from {@code config/jadm/quests.json}. The file is written with the bundled starting pool
 * when it doesn't exist yet, and read again with {@code /jadm quests reload}.
 */
public final class QuestPool {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("jadm").resolve("quests.json");
    private static final String BUNDLED = "/jadm/quests.json";
    private static final Gson GSON = new Gson();

    private static volatile Map<String, QuestDef> quests;

    private QuestPool() {
    }

    /** The quests by id, read on first use. */
    public static Map<String, QuestDef> get() {
        Map<String, QuestDef> q = quests;
        if (q == null) {
            load();
            q = quests;
        }
        return q;
    }

    public static QuestDef quest(String id) {
        return get().get(id);
    }

    /**
     * Reads the file again (writing the starting pool first if there is none). A broken file keeps the quests read
     * before.
     *
     * @return what was wrong with it, empty if nothing
     */
    public static synchronized List<String> load() {
        List<String> problems = new ArrayList<>();
        try {
            if (!Files.isRegularFile(FILE)) {
                Files.createDirectories(FILE.getParent());
                try (InputStream in = QuestPool.class.getResourceAsStream(BUNDLED)) {
                    if (in == null) {
                        throw new IOException("The mod has no " + BUNDLED);
                    }
                    Files.copy(in, FILE);
                }
                Jadm.LOGGER.info("Wrote the starting quests to {}", FILE);
            }
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                quests = parse(GSON.fromJson(reader, JsonObject.class), problems);
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            problems.add("Could not read " + FILE + ": " + e.getMessage());
            Jadm.LOGGER.error("Could not read {}", FILE, e);
            if (quests == null) {
                quests = bundled();
            }
        }
        problems.forEach(p -> Jadm.LOGGER.warn("quests.json: {}", p));
        return problems;
    }

    /** The bundled starting pool, for when the server's file can't be read at all. */
    static Map<String, QuestDef> bundled() {
        try (InputStream in = QuestPool.class.getResourceAsStream(BUNDLED)) {
            if (in != null) {
                return parse(GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class),
                        new ArrayList<>());
            }
        } catch (IOException | JsonParseException e) {
            Jadm.LOGGER.error("Could not read the bundled quests", e);
        }
        return Map.of();
    }

    static Map<String, QuestDef> parse(JsonObject root, List<String> problems) {
        if (root == null || !root.has("quests")) {
            throw new JsonParseException("no \"quests\" list");
        }
        List<QuestDef> list = GSON.fromJson(root.get("quests"), new TypeToken<List<QuestDef>>() { }.getType());
        Map<String, QuestDef> byId = new LinkedHashMap<>();
        for (QuestDef def : list) {
            if (def == null) {
                continue;
            }
            List<String> wrong = def.check();
            if (!wrong.isEmpty()) {
                problems.addAll(wrong);
                continue;
            }
            if (byId.containsKey(def.id)) {
                problems.add(def.id + ": the id is used twice; the second one is skipped");
                continue;
            }
            byId.put(def.id, def);
        }
        return Collections.unmodifiableMap(byId);
    }
}
