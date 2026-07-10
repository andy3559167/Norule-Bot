package com.norule.musicbot.shorturl;

import com.norule.musicbot.ShortUrlService;
import com.norule.musicbot.domain.shorturl.ImageShare;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SqliteShortUrlPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void migratesViewCountsAndPersistsLogChannel() throws Exception {
        Path database = tempDir.resolve("short-url.db");
        createLegacySchema(database);

        SqliteShortUrlRepository shortUrls = new SqliteShortUrlRepository(database);
        SqliteImageShareRepository images = new SqliteImageShareRepository(database);
        long now = System.currentTimeMillis();
        shortUrls.save(new ShortUrlService.ShortUrlEntry(
                "link123", "https://example.com", now, now + 60_000L
        ));
        images.save(new ImageShare(
                "image12", "image12.png", "image/png", 8L, now, now + 60_000L,
                "", "abc123"
        ));

        assertEquals(1L, shortUrls.incrementViewCount("link123"));
        assertEquals(2L, shortUrls.incrementViewCount("link123"));
        assertEquals(2L, shortUrls.findByCode("link123").getViewCount());
        assertEquals(1L, images.incrementViewCount("image12"));
        assertEquals(1L, images.findByCode("image12").viewCount());

        shortUrls.saveLogChannelId(987654321L);
        assertEquals(987654321L, shortUrls.findLogChannelId());
        shortUrls.saveLogChannelId(null);
        assertNull(shortUrls.findLogChannelId());
    }

    private void createLegacySchema(Path database) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE short_urls (
                        code TEXT PRIMARY KEY,
                        target TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE short_url_images (
                        code TEXT PRIMARY KEY,
                        storage_name TEXT NOT NULL,
                        content_type TEXT NOT NULL,
                        size_bytes INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        password_hash TEXT NOT NULL DEFAULT ''
                    )
                    """);
        }
    }
}
