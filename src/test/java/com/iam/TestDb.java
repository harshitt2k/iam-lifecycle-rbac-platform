package com.iam;

import com.iam.util.DbConnection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Rebuilds the *_test database from sql/schema.sql + sql/seed.sql before each test. */
final class TestDb {

    private TestDb() { }

    static void reset() throws Exception {
        String db = DbConnection.databaseName();
        if (!db.endsWith("_test")) {
            throw new IllegalStateException(
                    "Refusing to reset '" + db + "': tests only run against a database whose name ends in _test");
        }
        try (Connection c = DbConnection.getServerConnection(); Statement st = c.createStatement()) {
            for (String file : List.of("sql/schema.sql", "sql/seed.sql")) {
                String script = Files.readString(Path.of(file)).replaceAll("\\biam_platform\\b", db);
                for (String sql : split(script)) {
                    st.execute(sql);
                }
            }
        }
    }

    /** Minimal splitter: skips comment lines and honours DELIMITER (needed for the triggers). */
    static List<String> split(String script) {
        List<String> out = new ArrayList<>();
        String delimiter = ";";
        StringBuilder buf = new StringBuilder();
        for (String line : script.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("--")) continue;
            if (t.toUpperCase().startsWith("DELIMITER ")) {
                delimiter = t.substring(10).trim();
                continue;
            }
            buf.append(line).append('\n');
            if (t.endsWith(delimiter)) {
                String stmt = buf.toString().trim();
                out.add(stmt.substring(0, stmt.length() - delimiter.length()).trim());
                buf.setLength(0);
            }
        }
        if (buf.toString().trim().length() > 0) out.add(buf.toString().trim());
        return out;
    }
}
