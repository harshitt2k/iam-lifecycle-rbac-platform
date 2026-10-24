package com.iam.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DbConnection {

    private static final Properties PROPS = new Properties();

    static {
        // -Diam.config=... lets the tests point at a different database later
        String path = System.getProperty("iam.config", "config.properties");
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
}