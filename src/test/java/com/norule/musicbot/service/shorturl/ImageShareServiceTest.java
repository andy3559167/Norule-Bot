package com.norule.musicbot.service.shorturl;

import com.norule.musicbot.ShortUrlService;
import com.norule.musicbot.domain.shorturl.ImageShare;
import com.norule.musicbot.shorturl.ImageShareRepository;
import com.norule.musicbot.shorturl.ImageShareStorage;
import com.norule.musicbot.shorturl.ShortUrlRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }
}
