package com.norule.musicbot.domain.shorturl;

public record ImageShare(
        String code,
        String storageName,
        String contentType,
        long sizeBytes,
        long createdAt,
        long expiresAt,
        String passwordHash,
        String contentHash,
        long viewCount
) {
    public ImageShare(String code,
                      String storageName,
                      String contentType,
                      long sizeBytes,
                      long createdAt,
                      long expiresAt,
                      String passwordHash,
                      String contentHash) {
        this(code, storageName, contentType, sizeBytes, createdAt, expiresAt, passwordHash, contentHash, 0L);
    }

    public boolean isPasswordProtected() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public ImageShare withViewCount(long updatedViewCount) {
        return new ImageShare(code, storageName, contentType, sizeBytes, createdAt, expiresAt,
                passwordHash, contentHash, Math.max(0L, updatedViewCount));
    }
}
