package com.norule.musicbot.service.shorturl;

import com.norule.musicbot.ShortUrlService;
import com.norule.musicbot.domain.shorturl.ImageShare;
import com.norule.musicbot.shorturl.ImageShareRepository;
import com.norule.musicbot.shorturl.ImageShareStorage;
import com.norule.musicbot.shorturl.ShortUrlRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageShareServiceTest {
    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Test
    void createsPasswordProtectedImageWithDatePassword() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);
        ImageShareService service = createService(clock, new ImageShareService.Options(
                true, 60L * 60L * 1000L, 365L * 24L * 60L * 60L * 1000L, 20L * 1024L * 1024L,
                60_000L, 7
        ));

        ImageShareService.UploadResult result = service.create(new ImageShareService.Upload(PNG, true, "", 0L));

        assertTrue(result.isSuccess());
        assertNotNull(result.imageShare());
        assertEquals(7, result.imageShare().code().length());
        assertFalse(result.imageShare().code().startsWith("image-"));
        assertTrue(result.imageShare().isPasswordProtected());
        assertTrue(service.verifyPassword(result.imageShare(), "0711"));
        assertFalse(service.verifyPassword(result.imageShare(), "1234"));
        assertEquals(clock.millis() + 60L * 60L * 1000L, result.imageShare().expiresAt());
        assertEquals(1L, service.recordView(result.imageShare()).viewCount());
    }

    @Test
    void rejectsImageOverConfiguredLimitAndLongRetention() {
        ImageShareService limitedSizeService = createService(Clock.systemUTC(), new ImageShareService.Options(
                true, 60L * 60L * 1000L, 24L * 60L * 60L * 1000L, 7L,
                60_000L, 7
        ));
        ImageShareService limitedRetentionService = createService(Clock.systemUTC(), new ImageShareService.Options(
                true, 60L * 60L * 1000L, 24L * 60L * 60L * 1000L, 20L * 1024L * 1024L,
                60_000L, 7
        ));

        ImageShareService.UploadResult oversized = limitedSizeService.create(new ImageShareService.Upload(PNG, false, "", 0L));
        ImageShareService.UploadResult tooLong = limitedRetentionService.create(new ImageShareService.Upload(PNG, false, "", 2L * 24L * 60L * 60L * 1000L));

        assertEquals(ImageShareService.UploadError.IMAGE_TOO_LARGE, oversized.error());
        assertEquals(ImageShareService.UploadError.RETENTION_TOO_LONG, tooLong.error());
    }

    @Test
    void rejectsBlankProtectedPasswordWhenDateDefaultIsDisabled() {
        ImageShareService service = createService(Clock.systemUTC(), new ImageShareService.Options(
                true, 60L * 60L * 1000L, 24L * 60L * 60L * 1000L,
                20L * 1024L * 1024L, 100L * 1024L * 1024L, 5L * 60L * 1000L,
                30L * 24L * 60L * 60L * 1000L, 60_000L, 7,
                false, 8, 128, 80));

        ImageShareService.UploadResult result = service.create(
                new ImageShareService.Upload(PNG, true, "", 0L));

        assertEquals(ImageShareService.UploadError.PASSWORD_REQUIRED, result.error());
    }

    @Test
    void reusesTheActiveLinkWhenImageAccessAndRetentionMatch() {
        ImageShareService service = createService(Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC), new ImageShareService.Options(
                true, 60L * 60L * 1000L, 24L * 60L * 60L * 1000L, 20L * 1024L * 1024L,
                60_000L, 7
        ));

        ImageShareService.UploadResult first = service.create(new ImageShareService.Upload(PNG, false, "", 0L));
        ImageShareService.UploadResult second = service.create(new ImageShareService.Upload(PNG.clone(), false, "", 0L));

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertEquals(first.imageShare().code(), second.imageShare().code());
        assertFalse(second.imageShare().isPasswordProtected());
        assertNotEquals("", first.imageShare().contentHash());
    }

    @Test
    void createsDifferentLinksWhenPasswordOrRetentionDiffers() {
        ImageShareService service = createService(Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC), new ImageShareService.Options(
                true, 60L * 60L * 1000L, 24L * 60L * 60L * 1000L, 20L * 1024L * 1024L,
                60_000L, 7
        ));

        ImageShareService.UploadResult unprotected = service.create(new ImageShareService.Upload(PNG, false, "", 60L * 60L * 1000L));
        ImageShareService.UploadResult password1234 = service.create(new ImageShareService.Upload(PNG, true, "1234", 60L * 60L * 1000L));
        ImageShareService.UploadResult password5678 = service.create(new ImageShareService.Upload(PNG, true, "5678", 60L * 60L * 1000L));
        ImageShareService.UploadResult threeHours = service.create(new ImageShareService.Upload(PNG, false, "", 3L * 60L * 60L * 1000L));

        assertTrue(unprotected.isSuccess());
        assertTrue(password1234.isSuccess());
        assertTrue(password5678.isSuccess());
        assertTrue(threeHours.isSuccess());
        assertNotEquals(unprotected.imageShare().code(), password1234.imageShare().code());
        assertNotEquals(password1234.imageShare().code(), password5678.imageShare().code());
        assertNotEquals(unprotected.imageShare().code(), threeHours.imageShare().code());
    }

    @Test
    void reusesOnlyTheSameCustomExpiration() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);
        ImageShareService service = createService(clock, new ImageShareService.Options(
                true, 60L * 60L * 1000L, 365L * 24L * 60L * 60L * 1000L, 20L * 1024L * 1024L,
                60_000L, 7
        ));
        long firstExpiration = clock.millis() + 30L * 60L * 1000L;
        long secondExpiration = clock.millis() + 60L * 60L * 1000L;

        ImageShareService.UploadResult first = service.create(new ImageShareService.Upload(PNG, false, "", 0L, firstExpiration));
        ImageShareService.UploadResult duplicate = service.create(new ImageShareService.Upload(PNG.clone(), false, "", 0L, firstExpiration));
        ImageShareService.UploadResult differentExpiration = service.create(new ImageShareService.Upload(PNG, false, "", 0L, secondExpiration));

        assertEquals(first.imageShare().code(), duplicate.imageShare().code());
        assertNotEquals(first.imageShare().code(), differentExpiration.imageShare().code());
    }

    @Test
    void acceptsFiveMinuteVideoAndRejectsLongerOrOversizedVideo() throws Exception {
        ImageShareService service = createService(Clock.systemUTC(), new ImageShareService.Options(
                true, 60L * 60L * 1000L, 24L * 60L * 60L * 1000L,
                20L * 1024L * 1024L, 100L * 1024L * 1024L, 5L * 60L * 1000L,
                60_000L, 7
        ));
        ImageShareService smallLimitService = createService(Clock.systemUTC(), new ImageShareService.Options(
                true, 60L * 60L * 1000L, 24L * 60L * 60L * 1000L,
                20L * 1024L * 1024L, 32L, 5L * 60L * 1000L,
                60_000L, 7
        ));

        ImageShareService.UploadResult accepted = service.create(
                new ImageShareService.Upload(mp4(300_000L), false, "", 0L));
        ImageShareService.UploadResult tooLong = service.create(
                new ImageShareService.Upload(mp4(300_001L), false, "", 0L));
        ImageShareService.UploadResult tooLarge = smallLimitService.create(
                new ImageShareService.Upload(mp4(300_000L), false, "", 0L));

        assertTrue(accepted.isSuccess());
        assertTrue(accepted.imageShare().isVideo());
        assertEquals("video/mp4", accepted.imageShare().contentType());
        assertEquals(ImageShareService.UploadError.VIDEO_TOO_LONG, tooLong.error());
        assertEquals(ImageShareService.UploadError.VIDEO_TOO_LARGE, tooLarge.error());
    }

    @Test
    void archivesExpiredMediaInsteadOfDeletingIt() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-11T12:00:00Z"));
        InMemoryImageRepository repository = new InMemoryImageRepository();
        InMemoryImageStorage storage = new InMemoryImageStorage();
        long oneMinute = 60L * 1000L;
        long retention = 30L * 24L * 60L * 60L * 1000L;
        ImageShareService service = new ImageShareService(
                repository,
                new InMemoryShortUrlRepository(),
                storage,
                new ImageShareService.Options(
                        true, oneMinute, 365L * 24L * 60L * 60L * 1000L,
                        20L * 1024L * 1024L, 100L * 1024L * 1024L, 5L * 60L * 1000L,
                        retention, oneMinute, 7
                ),
                clock
        );

        ImageShare video = service.create(new ImageShareService.Upload(mp4(1_000L), false, "", oneMinute)).imageShare();
        ImageShare image = service.create(new ImageShareService.Upload(PNG, false, "", oneMinute)).imageShare();

        clock.advanceMillis(oneMinute);
        assertNull(service.resolve(video.code()));
        assertNull(service.open(video));
        assertNull(service.resolve(image.code()));
        assertNull(service.open(image));
        assertNotNull(service.findExpired(video.code()));
        assertFalse(storage.exists(video));
        assertFalse(storage.exists(image));
        assertTrue(storage.existsArchived(video));
        assertTrue(storage.existsArchived(image));

        service.cleanupExpired();
        assertNotNull(service.findExpired(image.code()));

        clock.advanceMillis(retention);
        service.cleanupExpired();
        assertFalse(storage.exists(video));
        assertTrue(storage.existsArchived(video));
        assertNotNull(service.findExpired(video.code()));
        assertNotNull(service.findExpired(image.code()));
    }

    @Test
    void reportsStorageFailuresWithoutUsingTheGenericCreateError() {
        ImageShareStorage failingStorage = new ImageShareStorage() {
            @Override
            public void save(ImageShare imageShare, byte[] content) throws IOException {
                throw new IOException("storage is read-only");
            }

            @Override
            public InputStream open(ImageShare imageShare) {
                return null;
            }

            @Override
            public boolean exists(ImageShare imageShare) {
                return false;
            }

            @Override
            public void delete(ImageShare imageShare) {
                // No file was written.
            }
        };
        ImageShareService service = new ImageShareService(
                new InMemoryImageRepository(),
                new InMemoryShortUrlRepository(),
                failingStorage,
                new ImageShareService.Options(
                        true, 60L * 60L * 1000L, 365L * 24L * 60L * 60L * 1000L,
                        20L * 1024L * 1024L, 60_000L, 7
                )
        );

        ImageShareService.UploadResult result = service.create(new ImageShareService.Upload(PNG, false, "", 0L));

        assertEquals(ImageShareService.UploadError.STORAGE_FAILED, result.error());
    }

    private byte[] mp4(long durationMillis) throws Exception {
        byte[] movieHeader = atom("mvhd", output -> {
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(1000);
            output.writeInt((int) durationMillis);
        });
        byte[] handler = atom("hdlr", output -> {
            output.writeInt(0);
            output.writeInt(0);
            output.writeBytes("vide");
        });
        byte[] media = atom("mdia", output -> output.write(handler));
        byte[] track = atom("trak", output -> output.write(media));
        byte[] movie = atom("moov", output -> {
            output.write(movieHeader);
            output.write(track);
        });
        return concat(atom("ftyp", output -> output.writeBytes("isom")), movie);
    }

    private byte[] atom(String type, AtomPayloadWriter writer) throws Exception {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
            writer.write(payload);
        }
        ByteArrayOutputStream atomBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(atomBytes)) {
            output.writeInt(payloadBytes.size() + 8);
            output.write(type.getBytes(StandardCharsets.US_ASCII));
            output.write(payloadBytes.toByteArray());
        }
        return atomBytes.toByteArray();
    }

    private byte[] concat(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] value : values) {
            output.writeBytes(value);
        }
        return output.toByteArray();
    }

    @FunctionalInterface
    private interface AtomPayloadWriter {
        void write(DataOutputStream output) throws Exception;
    }

    private ImageShareService createService(Clock clock, ImageShareService.Options options) {
        return new ImageShareService(
                new InMemoryImageRepository(),
                new InMemoryShortUrlRepository(),
                new InMemoryImageStorage(),
                options,
                clock
        );
    }

    private static final class InMemoryImageRepository implements ImageShareRepository {
        private final Map<String, ImageShare> images = new LinkedHashMap<>();

        @Override
        public ImageShare findByCode(String code) {
            return images.get(code);
        }

        @Override
        public List<ImageShare> findActiveByContentHash(String contentHash, long nowMillis) {
            return images.values().stream()
                    .filter(image -> contentHash.equals(image.contentHash()) && image.expiresAt() > nowMillis)
                    .toList();
        }

        @Override
        public void save(ImageShare imageShare) {
            images.put(imageShare.code(), imageShare);
        }

        @Override
        public void deleteByCode(String code) {
            images.remove(code);
        }

        @Override
        public List<ImageShare> findExpired(long nowMillis) {
            return images.values().stream().filter(image -> image.expiresAt() <= nowMillis).toList();
        }

        @Override
        public long incrementViewCount(String code) {
            ImageShare imageShare = images.get(code);
            if (imageShare == null) {
                return 0L;
            }
            long updated = imageShare.viewCount() + 1L;
            images.put(code, imageShare.withViewCount(updated));
            return updated;
        }
    }

    private static final class InMemoryShortUrlRepository implements ShortUrlRepository {
        @Override
        public ShortUrlService.ShortUrlEntry findByCode(String code) {
            return null;
        }

        @Override
        public ShortUrlService.ShortUrlEntry findActiveByTarget(String target, long nowMillis) {
            return null;
        }

        @Override
        public void save(ShortUrlService.ShortUrlEntry entry) {
            // Not needed by image-share tests.
        }

        @Override
        public void deleteByCode(String code) {
            // Not needed by image-share tests.
        }

        @Override
        public int cleanupExpired(long nowMillis) {
            return 0;
        }

        @Override
        public long incrementViewCount(String code) {
            return 0L;
        }

        @Override
        public Long findLogChannelId() {
            return null;
        }

        @Override
        public void saveLogChannelId(Long channelId) {
            // Not needed by image-share tests.
        }
    }

    private static final class InMemoryImageStorage implements ImageShareStorage {
        private final Map<String, byte[]> files = new LinkedHashMap<>();
        private final Map<String, byte[]> archivedFiles = new LinkedHashMap<>();

        @Override
        public void save(ImageShare imageShare, byte[] content) {
            files.put(imageShare.storageName(), content.clone());
        }

        @Override
        public InputStream open(ImageShare imageShare) {
            return new ByteArrayInputStream(files.get(imageShare.storageName()));
        }

        @Override
        public boolean exists(ImageShare imageShare) {
            return files.containsKey(imageShare.storageName());
        }

        @Override
        public void delete(ImageShare imageShare) {
            files.remove(imageShare.storageName());
        }

        @Override
        public String archive(ImageShare imageShare) {
            byte[] content = files.remove(imageShare.storageName());
            if (content != null) {
                archivedFiles.put(imageShare.storageName(), content);
            }
            return imageShare.storageName();
        }

        @Override
        public boolean existsArchived(ImageShare imageShare) {
            return archivedFiles.containsKey(imageShare.storageName());
        }
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
