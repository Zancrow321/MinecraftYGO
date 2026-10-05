package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.data.CardData;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.ffi.DuelDataSource;
import io.github.zancrow321.minecraftygo.engine.ffi.LogType;
import io.github.zancrow321.minecraftygo.engine.script.ScriptProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Connects a duel to the card database and script provider; collects script errors for diagnostics. */
public final class EngineDataSource implements DuelDataSource {
	private static final Logger LOGGER = LoggerFactory.getLogger(EngineDataSource.class);

	private final CardDatabase database;
	private final ScriptProvider scripts;
	private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

	public EngineDataSource(CardDatabase database, ScriptProvider scripts) {
		this.database = database;
		this.scripts = scripts;
	}

	@Override
	public Optional<CardData> card(int code) {
		return database.find(code);
	}

	@Override
	public Optional<byte[]> script(String name) {
		return scripts.read(name);
	}

	@Override
	public void log(LogType type, String message) {
		if (type == LogType.ERROR) {
			errors.add(message);
			LOGGER.warn("ocgcore: {}", message);
		} else {
			LOGGER.debug("ocgcore [{}]: {}", type, message);
		}
	}

	/** Errors reported by the core so far (Lua errors, missing scripts of effect cards, ...). */
	public List<String> errors() {
		synchronized (errors) {
			return List.copyOf(errors);
		}
	}
}
