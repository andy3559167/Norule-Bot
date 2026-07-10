package com.norule.musicbot.service.shorturl;

import com.norule.musicbot.domain.shorturl.ImageShare;
import com.norule.musicbot.domain.shorturl.ImageShareDomainService;
import com.norule.musicbot.shorturl.ImageShareRepository;
import com.norule.musicbot.shorturl.ImageShareStorage;
import com.norule.musicbot.shorturl.ShortUrlRepository;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ImageShareService {
    public enum UploadError {
        DISABLED,
        IMAGE_REQUIRED,
        UNSUPPORTED_IMAGE,
        IMAGE_TOO_LARGE,
        RETENTION_TOO_LONG,
        INVALID_PASSWORD,
        CREATE_FAILED
    }

    public record Options(
            boolean enabled,
            long defaultRetentionMillis,
            long maxRetentionMillis,
            long maxFileSizeBytes,
            long cleanupIntervalMillis,
            int codeLength
    ) {
        private static final long MAX_RETENTION_MILLIS = 365L * 24L * 60L * 60L * 1000L;
        private static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;

        public Options {
            maxRetentionMillis = Math.max(1L, Math.min(MAX_RETENTION_MILLIS, maxRetentionMillis));
            defaultRetentionMillis = Math.max(1L, Math.min(maxRetentionMillis, defaultRetentionMillis));
            maxFileSizeBytes = Math.max(1L, Math.min(MAX_FILE_SIZE_BYTES, maxFileSizeBytes));
            cleanupIntervalMillis = Math.max(60_000L, cleanupIntervalMillis);
            codeLength = Math.max(4, Math.min(32, codeLength));
        }
    }

    public record Upload(byte[] content, boolean passwordProtected, String password, long requestedRetentionMillis) {
    }

    public record UploadResult(ImageShare imageShare, UploadError error) {
        public boolean isSuccess() {
            return imageShare != null && error == null;
        }
    }

    private static final String IMAGE_CODE_PREFIX = "image-";
    private static final char[] RANDOM_CODE_ALPHABET = "23456789abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int PASSWORD_ITERATIONS = 120_000;
    private static final int PASSWORD_KEY_LENGTH_BITS = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ImageShareDomainService domainService = new ImageShareDomainService();
    private final ImageShareRepository imageRepository;
    private final ShortUrlRepository shortUrlRepository;
    private final ImageShareStorage storage;
    private final Clock clock;
    private final AtomicReference<Options> options;
    private volatile long lastCleanupAt;

    public ImageShareService(ImageShareRepository imageRepository,
                             ShortUrlRepository shortUrlRepository,
                             ImageShareStorage storage,
                             Options options) {
        this(imageRepository, shortUrlRepository, storage, options, Clock.systemDefaultZone());
    }

    public ImageShareService(ImageShareRepository imageRepository,
                             ShortUrlRepository shortUrlRepository,
                             ImageShareStorage storage,
                             Options options,
                             Clock clock) {
        if (imageRepository == null || shortUrlRepository == null || storage == null) {
            throw new IllegalArgumentException("image share dependencies cannot be null");
        }
        this.imageRepository = imageRepository;
        this.shortUrlRepository = shortUrlRepository;
        this.storage = storage;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.options = new AtomicReference<>(options == null
                ? new Options(true, 60L * 60L * 1000L, 365L * 24L * 60L * 60L * 1000L,
                20L * 1024L * 1024L, 10L * 60L * 1000L, 7)
                : options);
    }

    public UploadResult create(Upload upload) {
        Options currentOptions = options.get();
        if (!currentOptions.enabled()) {
            return new UploadResult(null, UploadError.DISABLED);
        }
        if (upload == null || upload.content() == null || upload.content().length == 0) {
            return new UploadResult(null, UploadError.IMAGE_REQUIRED);
        }
        if (upload.content().length > currentOptions.maxFileSizeBytes()) {
            return new UploadResult(null, UploadError.IMAGE_TOO_LARGE);
        }
        ImageShareDomainService.ImageType imageType = domainService.detectImageType(upload.content());
        if (imageType == null) {
            return new UploadResult(null, UploadError.UNSUPPORTED_IMAGE);
        }

        long retention = upload.requestedRetentionMillis() <= 0L
                ? currentOptions.defaultRetentionMillis()
                : upload.requestedRetentionMillis();
        if (retention > currentOptions.maxRetentionMillis()) {
            return new UploadResult(null, UploadError.RETENTION_TOO_LONG);
        }

        String passwordHash = "";
        if (upload.passwordProtected()) {
            String password = domainService.normalizePassword(upload.password());
            if (password.isBlank()) {
                password = domainService.defaultPassword(LocalDate.now(clock));
            }
            if (!domainService.isValidPassword(password)) {
                return new UploadResult(null, UploadError.INVALID_PASSWORD);
            }
            passwordHash = hashPassword(password);
        }

        long now = clock.millis();
        maybeCleanup(now);
        String code = nextAvailableCode(currentOptions.codeLength());
        if (code == null) {
            return new UploadResult(null, UploadError.CREATE_FAILED);
        }
        ImageShare imageShare = new ImageShare(
                code,
                code + "." + imageType.extension(),
                imageType.contentType(),
                upload.content().length,
                now,
                now + retention,
                passwordHash
        );
        try {
            storage.save(imageShare, upload.content());
            try {
                imageRepository.save(imageShare);
            } catch (RuntimeException e) {
                deleteStoredImage(imageShare);
                throw e;
            }
            return new UploadResult(imageShare, null);
        } catch (Exception ignored) {
            return new UploadResult(null, UploadError.CREATE_FAILED);
        }
    }

    public ImageShare resolve(String code) {
        if (code == null || code.isBlank() || !code.startsWith(IMAGE_CODE_PREFIX)) {
            return null;
        }
        long now = clock.millis();
        maybeCleanup(now);
        ImageShare imageShare = imageRepository.findByCode(code.trim());
        if (imageShare == null) {
            return null;
        }
        if (imageShare.expiresAt() <= now || !storage.exists(imageShare)) {
            delete(imageShare);
            return null;
        }
        return imageShare;
    }

    public InputStream open(ImageShare imageShare) {
        if (imageShare == null) {
            return null;
        }
        try {
            return storage.open(imageShare);
        } catch (Exception ignored) {
            delete(imageShare);
            return null;
        }
    }

    public boolean verifyPassword(ImageShare imageShare, String password) {
        if (imageShare == null || !imageShare.isPasswordProtected()) {
            return imageShare != null;
        }
        String normalized = domainService.normalizePassword(password);
        return !normalized.isBlank() && verifyPasswordHash(normalized, imageShare.passwordHash());
    }

    public Options options() {
        return options.get();
    }

    public void updateOptions(Options updatedOptions) {
        if (updatedOptions != null) {
            options.set(updatedOptions);
        }
    }

    public void cleanupExpired() {
        long now = clock.millis();
        List<ImageShare> expired = imageRepository.findExpired(now);
        for (ImageShare imageShare : expired) {
            delete(imageShare);
        }
        lastCleanupAt = now;
    }

    private void maybeCleanup(long now) {
        if (now - lastCleanupAt < options.get().cleanupIntervalMillis()) {
            return;
        }
        synchronized (this) {
            if (now - lastCleanupAt >= options.get().cleanupIntervalMillis()) {
                cleanupExpired();
            }
        }
    }

    private String nextAvailableCode(int codeLength) {
        for (int attempt = 0; attempt < 10_000; attempt++) {
            String code = IMAGE_CODE_PREFIX + randomCode(codeLength);
            if (imageRepository.findByCode(code) == null && shortUrlRepository.findByCode(code) == null) {
                return code;
            }
        }
        return null;
    }

    private String randomCode(int length) {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = RANDOM_CODE_ALPHABET[SECURE_RANDOM.nextInt(RANDOM_CODE_ALPHABET.length)];
        }
        return new String(chars);
    }

    private void delete(ImageShare imageShare) {
        try {
            storage.delete(imageShare);
        } catch (Exception ignored) {
            // The metadata must still be removed so an unavailable or expired image cannot be served.
        }
        imageRepository.deleteByCode(imageShare.code());
    }

    private void deleteStoredImage(ImageShare imageShare) {
        try {
            storage.delete(imageShare);
        } catch (Exception ignored) {
            // The failed upload has no metadata and cannot be served; leave cleanup to the operator if deletion also fails.
        }
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = derivePasswordHash(password.toCharArray(), salt, PASSWORD_ITERATIONS);
        return PASSWORD_ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    private boolean verifyPasswordHash(String password, String encodedHash) {
        try {
            String[] parts = encodedHash.split(":", 3);
            if (parts.length != 3) {
                return false;
            }
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = derivePasswordHash(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ignored) {
            return false;
        }
    }

    private byte[] derivePasswordHash(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, PASSWORD_KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash image-share password", e);
        } finally {
            spec.clearPassword();
        }
    }
}
