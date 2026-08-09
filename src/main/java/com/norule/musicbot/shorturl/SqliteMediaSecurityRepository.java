package com.norule.musicbot.shorturl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

public final class SqliteMediaSecurityRepository extends JdbcMediaSecurityRepository {
    public SqliteMediaSecurityRepository(Path dbFilePath) {
        super(connectionProvider(dbFilePath), false);
    }

    private static ConnectionProvider connectionProvider(Path dbFilePath) {
        try {
            Class.forName("org.sqlite.JDBC");
            Path normalized = dbFilePath.toAbsolutePath().normalize();
            Path parent = normalized.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String jdbcUrl = "jdbc:sqlite:" + normalized;
            return () -> DriverManager.getConnection(jdbcUrl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare media security sqlite repository", e);
        }
    }
}
