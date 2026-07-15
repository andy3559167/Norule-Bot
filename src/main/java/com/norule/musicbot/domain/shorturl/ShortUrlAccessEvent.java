package com.norule.musicbot.domain.shorturl;

public record ShortUrlAccessEvent(
        Action action,
        ResourceType resourceType,
        String code,
        String publicUrl,
        String target,
        long viewCount,
        long expiresAt,
        boolean passwordProtected,
        long fileSizeBytes,
        String creatorDiscordUserId,
        String clientAddress,
        String userAgent,
        long occurredAt
) {
    public enum Action {
        CREATED,
        VIEWED
    }

    public enum ResourceType {
        URL,
        IMAGE,
        VIDEO
    }
}
