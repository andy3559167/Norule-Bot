package com.norule.musicbot.service.shorturl;

import com.norule.musicbot.domain.shorturl.ImageShare;
import com.norule.musicbot.domain.shorturl.MediaStorageState;
import com.norule.musicbot.service.shorturl.AnonymousDeviceIdentityService.DeviceIdentity;
import com.norule.musicbot.shorturl.SqliteImageShareRepository;
import com.norule.musicbot.shorturl.SqliteMediaSecurityRepository;
import com.norule.musicbot.shorturl.SqliteShortUrlRepository;
import com.norule.musicbot.shorturl.infra.FileSystemImageShareStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaArchiveLifecycleTest {
    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @TempDir
    Path tempDir;

    @Test
    void expiredMediaArchivesReleasesUserQuotaButKeepsGlobalBytesUntilManualDeletion() throws Exception {
        Path database = tempDir.resolve("archive.db");
        SqliteImageShareRepository images = new SqliteImageShareRepository(database);
        SqliteMediaSecurityRepository security = new SqliteMediaSecurityRepository(database);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T12:00:00Z"));
        Path active = tempDir.resolve("active");
        Path archive = tempDir.resolve("expired");
        FileSystemImageShareStorage storage = new FileSystemImageShareStorage(
                active, tempDir.resolve("tmp"), archive);
        ImageShareService service = service(database, images, security, storage, clock);
        AnonymousDeviceIdentityService identities = new AnonymousDeviceIdentityService(
                security, AnonymousDeviceIdentityService.Options.defaults(),
                "quota-secret", "device-secret", clock);
        DeviceIdentity device = identities.resolveAnonymous("", "203.0.113.5");

        ImageShare created = service.create(new ImageShareService.Upload(
                PNG, false, "", 60_000L), device.quotaSubject()).imageShare();
        assertNotNull(created);
        assertEquals(PNG.length, security.activeStorageBytes(device.quotaSubject().quotaGroupId()));

        clock.advanceMillis(60_000L);
        assertNull(service.resolve(created.code()));
        ImageShare archived = images.findByCode(created.code());
        assertEquals(MediaStorageState.ARCHIVED, archived.storageState());
        assertFalse(Files.exists(active.resolve(created.storageName())));
        assertTrue(Files.exists(archive.resolve(created.storageName())));
        assertNull(service.open(archived));
        assertEquals(0L, security.activeStorageBytes(device.quotaSubject().quotaGroupId()));
        assertEquals(PNG.length, security.globalManagedStorageBytes());

        Files.delete(archive.resolve(created.storageName()));
        service.cleanupExpired();
        assertEquals(MediaStorageState.ARCHIVE_DELETED,
                images.findByCode(created.code()).storageState());
        assertEquals(0L, security.globalManagedStorageBytes());
    }

    @Test
    void archiveFailureStaysPrivateAndBackgroundRetryEventuallyArchives() throws Exception {
        Path database = tempDir.resolve("retry.db");
        SqliteImageShareRepository images = new SqliteImageShareRepository(database);
        SqliteMediaSecurityRepository security = new SqliteMediaSecurityRepository(database);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T12:00:00Z"));
        Path active = tempDir.resolve("retry-active");
        Path archive = tempDir.resolve("blocked-archive");
        Files.writeString(archive, "not a directory");
        FileSystemImageShareStorage storage = new FileSystemImageShareStorage(
                active, tempDir.resolve("retry-tmp"), archive);
        ImageShareService service = service(database, images, security, storage, clock);
        ImageShare created = service.create(new ImageShareService.Upload(PNG, false, "", 60_000L)).imageShare();

        clock.advanceMillis(60_000L);
        assertNull(service.resolve(created.code()));
        assertEquals(MediaStorageState.ARCHIVE_PENDING,
                images.findByCode(created.code()).storageState());
        assertTrue(Files.exists(active.resolve(created.storageName())));
        assertNull(service.open(images.findByCode(created.code())));

        Files.delete(archive);
        Files.createDirectories(archive);
        service.cleanupExpired();
        assertEquals(MediaStorageState.ARCHIVED,
                images.findByCode(created.code()).storageState());
        assertTrue(Files.exists(archive.resolve(created.storageName())));
    }

    private ImageShareService service(Path database, SqliteImageShareRepository images,
                                      SqliteMediaSecurityRepository security,
                                      FileSystemImageShareStorage storage, Clock clock) {
        MediaPasswordAttemptGuard passwordGuard = new MediaPasswordAttemptGuard(
                security, MediaPasswordAttemptGuard.Options.defaults(), "password-secret", clock);
        MediaQuotaService.Options defaults = MediaQuotaService.Options.defaults();
        MediaQuotaService quota = new MediaQuotaService(security,
                new MediaQuotaService.Options(true, defaults.anonymous(), defaults.authenticated(),
                        1024L * 1024L), clock);
        return new ImageShareService(images, new SqliteShortUrlRepository(database), storage,
                new ImageShareService.Options(true, 60_000L, 24L * 60L * 60L * 1000L,
                        20L * 1024L * 1024L, 100L * 1024L * 1024L, 5L * 60L * 1000L,
                        30L * 24L * 60L * 60L * 1000L, 60_000L, 7),
                clock, new ImageShareService.SecurityDependencies(passwordGuard, quota));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }
    }
}
