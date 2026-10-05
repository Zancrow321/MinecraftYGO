package io.github.zancrow321.minecraftygo.engine.testing;

import io.github.zancrow321.minecraftygo.engine.data.CardData;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads EDOPro/BabelCDB {@code .cdb} files (SQLite) fully into memory. Later files override earlier ones. */
public final class SqliteCardDatabase implements CardDatabase {
	private final Map<Integer, CardData> cards = new HashMap<>();
	private final Map<Integer, String> names = new HashMap<>();

	public SqliteCardDatabase(List<Path> files) {
		for (Path file : files) {
			if (!Files.isRegularFile(file)) {
				throw new IllegalArgumentException("Missing card database " + file);
			}
			load(file);
		}
	}

	private void load(Path file) {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
				Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery(
						"SELECT d.id, d.alias, d.setcode, d.type, d.atk, d.def, d.level, d.race, d.attribute, t.name "
								+ "FROM datas d LEFT JOIN texts t ON t.id = d.id")) {
			while (rows.next()) {
				int code = rows.getInt(1);
				cards.put(code, CardData.fromDatabaseRow(code, rows.getInt(2), rows.getLong(3), rows.getInt(4),
						rows.getInt(5), rows.getInt(6), rows.getLong(7), rows.getLong(8), rows.getInt(9)));
				String name = rows.getString(10);
				if (name != null) {
					names.put(code, name);
				}
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Failed to read " + file, e);
		}
	}

	@Override
	public Optional<CardData> find(int code) {
		return Optional.ofNullable(cards.get(code));
	}

	@Override
	public Collection<Integer> codes() {
		return Collections.unmodifiableSet(cards.keySet());
	}

	public Optional<String> name(int code) {
		return Optional.ofNullable(names.get(code));
	}

	public int size() {
		return cards.size();
	}
}
