package com.norule.musicbot.shorturl;

import com.norule.musicbot.domain.shorturl.ImageShare;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
                content_hash CHAR(64) NOT NULL DEFAULT '',
                view_count BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (code),
                KEY idx_short_url_images_expires (expires_at),
                KEY idx_short_url_images_content_hash_expires (content_hash, expires_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
    private static final String SELECT_BY_CODE = "SELECT code, storage_name, content_type, size_bytes, created_at, expires_at, password_hash, content_hash, view_count FROM short_url_images WHERE code = ?";
    private static final String SELECT_ACTIVE_BY_CONTENT_HASH = """
            SELECT code, storage_name, content_type, size_bytes, created_at, expires_at, password_hash, content_hash, view_count
            FROM short_url_images
            WHERE content_hash = ? AND expires_at > ?
            ORDER BY created_at DESC
            """;
    private static final String INSERT = "INSERT INTO short_url_images (code, storage_name, content_type, size_bytes, created_at, expires_at, password_hash, content_hash, view_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_BY_CODE = "DELETE FROM short_url_images WHERE code = ?";
    private static final String SELECT_EXPIRED = "SELECT code, storage_name, content_type, size_bytes, created_at, expires_at, password_hash, content_hash, view_count FROM short_url_images WHERE expires_at <= ?";
    private static final String INCREMENT_VIEW_COUNT = "UPDATE short_url_images SET view_count = view_count + 1 WHERE code = ?";
    private static final String SELECT_VIEW_COUNT = "SELECT view_count FROM short_url_images WHERE code = ?";

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
    public List<ImageShare> findActiveByContentHash(String contentHash, long nowMillis) {
        if (contentHash == null || contentHash.isBlank()) {
            return List.of();
        }
        List<ImageShare> matches = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE_BY_CONTENT_HASH)) {
            statement.setString(1, contentHash);
            statement.setLong(2, nowMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    matches.add(mapRow(resultSet));
                }
            }
            return matches;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query image share by content hash", e);
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
            statement.setString(8, imageShare.contentHash());
            statement.setLong(9, imageShare.viewCount());
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
    public long incrementViewCount(String code) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(INCREMENT_VIEW_COUNT)) {
                update.setString(1, code);
                if (update.executeUpdate() == 0) {
                    return 0L;
                }
            }
            try (PreparedStatement select = connection.prepareStatement(SELECT_VIEW_COUNT)) {
                select.setString(1, code);
                try (ResultSet resultSet = select.executeQuery()) {
                    return resultSet.next() ? resultSet.getLong("view_count") : 0L;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to increment image share view count", e);
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
            ensureContentHashColumn(connection, statement);
            ensureViewCountColumn(connection, statement);
            ensureContentHashIndex(connection, statement);
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
                resultSet.getString("password_hash"),
                resultSet.getString("content_hash"),
                resultSet.getLong("view_count")
        );
    }

    private static void ensureContentHashColumn(Connection connection, Statement statement) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, "short_url_images", "content_hash")) {
            if (columns.next()) {
                return;
            }
        }
        statement.execute("ALTER TABLE short_url_images ADD COLUMN content_hash CHAR(64) NOT NULL DEFAULT ''");
    }

    private static void ensureContentHashIndex(Connection connection, Statement statement) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(connection.getCatalog(), null, "short_url_images", false, false)) {
            while (indexes.next()) {
                if ("idx_short_url_images_content_hash_expires".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        statement.execute("CREATE INDEX idx_short_url_images_content_hash_expires ON short_url_images(content_hash, expires_at)");
    }

    private static void ensureViewCountColumn(Connection connection, Statement statement) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, "short_url_images", "view_count")) {
            if (columns.next()) {
                return;
            }
        }
        statement.execute("ALTER TABLE short_url_images ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0");
    }
}
