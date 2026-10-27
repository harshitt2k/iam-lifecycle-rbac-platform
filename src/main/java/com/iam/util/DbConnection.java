package com.iam.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DbConnection {

    private static final Properties PROPS = new Properties();

    static {
        // Register the driver explicitly: inside Tomcat the jar sits in WEB-INF/lib, where
        // DriverManager's automatic discovery can miss it.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("MySQL driver not found on the classpath");
        }
        // -Diam.config=... (or the IAM_CONFIG environment variable) points at another config file:
        // the tests use it for the test database, Tomcat uses it to find config.properties.
        String path = System.getProperty("iam.config");
        if (path == null || path.isBlank()) path = System.getenv("IAM_CONFIG");
        if (path == null || path.isBlank()) path = "config.properties";
        try (InputStream in = new FileInputStream(path)) {
            PROPS.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Cannot read " + new File(path).getAbsolutePath()
                    + ": " + e.getMessage());
        }
    }

    private DbConnection() { }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(
                PROPS.getProperty("db.url"),
                PROPS.getProperty("db.user"),
                PROPS.getProperty("db.password", ""));
    }

    /** Connection to the MySQL server without selecting a database (used to rebuild the test DB). */
    public static Connection getServerConnection() throws SQLException {
        String url = PROPS.getProperty("db.url").replaceFirst("(jdbc:mysql://[^/]+/)[^?]*", "$1");
        return DriverManager.getConnection(url, PROPS.getProperty("db.user"), PROPS.getProperty("db.password", ""));
    }

    public static String databaseName() {
        Matcher m = Pattern.compile("jdbc:mysql://[^/]+/([^?]+)").matcher(PROPS.getProperty("db.url", ""));
        return m.find() ? m.group(1) : "";
    }
}
