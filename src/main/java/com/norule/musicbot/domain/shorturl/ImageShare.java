package com.norule.musicbot.domain.shorturl;

public record ImageShare(
        String code,
        String storageName,
        String contentType,
        long sizeBytes,
        long createdAt,
        long expiresAt,
        String passwordHash
) {
    public boolean isPasswordProtected() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
