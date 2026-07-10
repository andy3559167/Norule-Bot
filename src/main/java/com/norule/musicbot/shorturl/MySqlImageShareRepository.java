package com.norule.musicbot.shorturl;

import com.norule.musicbot.domain.shorturl.ImageShare;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class MySqlImageShareRepository implements ImageShareRepository, AutoCloseable {
    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS short_url_images (
                code VARCHAR(64) NOT NULL,
                storage_name VARCHAR(128) NOT NULL,
                content_type VARCHAR(128) NOT NULL,
                size_bytes BIGINT NOT NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                password_hash VARCHAR(512) NOT NULL DEFAULT '',
                PRIMARY KEY (code),
                KEY idx_short_url_images_expires (expires_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
    private static final String SELECT_BY_CODE = "SELECT code, storage_name, content_type, size_bytes, created_at, expires_at, password_hash FROM short_url_images WHERE code = ?";
    private static final String INSERT = "INSERT INTO short_url_images (code, storage_name, content_type, size_bytes, created_at, expires_at, password_hash) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_BY_CODE = "DELETE FROM short_url_images WHERE code = ?";
    private static final String SELECT_EXPIRED = "SELECT code, storage_name, content_type, size_bytes, created_at, expires_at, password_hash FROM short_url_images WHERE expires_at <= ?";

    private final HikariDataSource dataSource;

    public MySqlImageShareRepository(String jdbcUrl, String username, String password, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(Math.max(2, poolSize));
        config.setMinimumIdle(1);
        config.setPoolName("image-share-pool");
        config.setConnectionTimeout(10_000L);
        config.setValidationTimeout(5_000L);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.dataSource = new HikariDataSource(config);
        initializeSchema(dataSource);
    }

    @Override
    public ImageShare findByCode(String code) {
        try (Connection connection = dataSource.getConnection();
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
        try (Connection connection = dataSource.getConnection();
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
        try (Connection connection = dataSource.getConnection();
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
        try (Connection connection = dataSource.getConnection();
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

    @Override
    public void close() {
        dataSource.close();
    }

    private static void initializeSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize image-share mysql schema", e);
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
