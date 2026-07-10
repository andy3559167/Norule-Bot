package com.norule.musicbot.domain.shorturl;

public record ShortUrl(
        String code,
        String target,
        long createdAt,
        long expiresAt,
        long viewCount
) {
    public ShortUrl(String code, String target, long createdAt, long expiresAt) {
        this(code, target, createdAt, expiresAt, 0L);
    }
}
