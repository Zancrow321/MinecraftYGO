package io.github.zancrow321.minecraftygo.tools.pool;

import io.github.zancrow321.minecraftygo.engine.data.CardData;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reads BabelCDB {@code .cdb} files (SQLite, EDOPro schema). */
public final class CdbReader {
	private CdbReader() {}

	/** Card texts: name, description and the 16 effect strings ({@code str1..str16}). */
	public record Text(String name, String description, List<String> strings) {}

	public record Entry(CardData data, long category, Text text) {}

	public static Map<Integer, Entry> read(List<Path> files) {
		Map<Integer, Entry> entries = new TreeMap<>();
		for (Path file : files) {
			try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
					Statement statement = connection.createStatement();
					ResultSet rows = statement.executeQuery("SELECT d.id, d.alias, d.setcode, d.type, d.atk, d.def, d.level, "
							+ "d.race, d.attribute, d.category, t.name, t.desc, t.str1, t.str2, t.str3, t.str4, t.str5, t.str6, "
							+ "t.str7, t.str8, t.str9, t.str10, t.str11, t.str12, t.str13, t.str14, t.str15, t.str16 "
							+ "FROM datas d LEFT JOIN texts t ON t.id = d.id")) {
				while (rows.next()) {
					int code = rows.getInt(1);
					CardData data = CardData.fromDatabaseRow(code, rows.getInt(2), rows.getLong(3), rows.getInt(4),
							rows.getInt(5), rows.getInt(6), rows.getLong(7), rows.getLong(8), rows.getInt(9));
					List<String> strings = new ArrayList<>(16);
					for (int i = 0; i < 16; i++) {
						String value = rows.getString(13 + i);
						strings.add(value == null ? "" : value);
					}
					while (!strings.isEmpty() && strings.getLast().isEmpty()) {
						strings.removeLast();
					}
					Text text = new Text(nullToEmpty(rows.getString(11)), nullToEmpty(rows.getString(12)), strings);
					entries.put(code, new Entry(data, rows.getLong(10), text));
				}
			} catch (SQLException e) {
				throw new IllegalStateException("Failed to read " + file, e);
			}
		}
		return entries;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
