package com.norule.musicbot.shorturl;

import com.norule.musicbot.domain.shorturl.ImageShare;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class SqliteImageShareRepository implements ImageShareRepository {
    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS short_url_images (
                code TEXT PRIMARY KEY,
                storage_name TEXT NOT NULL,
                content_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                password_hash TEXT NOT NULL DEFAULT ''
            )
            """;
    private static final String CREATE_INDEX = "CREATE INDEX IF NOT EXISTS idx_short_url_images_expires ON short_url_images(expires_at)";
    private static final String SELECT_BY_CODE = "SELECT code, storage_name, content_type, size_bytes, created_at, expires_at, password_hash FROM short_url_images WHERE code = ?";
    private static final String INSERT = "INSERT INTO short_url_images (code, storage_name, content_type, size_bytes, created_at, expires_at, password_hash) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_BY_CODE = "DELETE FROM short_url_images WHERE code = ?";
    private static final String SELECT_EXPIRED = "SELECT code, storage_name, content_type, size_bytes, created_at, expires_at, password_hash FROM short_url_images WHERE expires_at <= ?";

    private final String jdbcUrl;

    public SqliteImageShareRepository(Path dbFilePath) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found (org.sqlite.JDBC)", e);
        }
        try {
            Path parent = dbFilePath.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare image-share sqlite directory", e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbFilePath.toAbsolutePath().normalize();
        initializeSchema();
    }

    @Override
    public ImageShare findByCode(String code) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_CODE)) {
            statement.setString(1, code);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRow(resultSet) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query image share by code", e);
        }
    }

    @Override
    public void save(ImageShare imageShare) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, imageShare.code());
            statement.setString(2, imageShare.storageName());
            statement.setString(3, imageShare.contentType());
            statement.setLong(4, imageShare.sizeBytes());
            statement.setLong(5, imageShare.createdAt());
            statement.setLong(6, imageShare.expiresAt());
            statement.setString(7, imageShare.passwordHash());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save image share", e);
        }
    }

    @Override
    public void deleteByCode(String code) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(DELETE_BY_CODE)) {
            statement.setString(1, code);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete image share", e);
        }
    }

    @Override
    public List<ImageShare> findExpired(long nowMillis) {
        List<ImageShare> expired = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(SELECT_EXPIRED)) {
            statement.setLong(1, nowMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    expired.add(mapRow(resultSet));
                }
            }
            return expired;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find expired image shares", e);
        }
    }

    private void initializeSchema() {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
            statement.execute(CREATE_INDEX);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize image-share sqlite schema", e);
        }
    }

    private ImageShare mapRow(ResultSet resultSet) throws SQLException {
        return new ImageShare(
                resultSet.getString("code"),
                resultSet.getString("storage_name"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getLong("created_at"),
                resultSet.getLong("expires_at"),
                resultSet.getString("password_hash")
        );
    }
}
