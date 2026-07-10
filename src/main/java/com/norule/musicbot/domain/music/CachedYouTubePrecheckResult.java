package com.norule.musicbot.domain.music;

import java.time.Instant;

public record CachedYouTubePrecheckResult(
        YouTubePlaybackPrecheckStatus status,
        Instant checkedAt,
        Instant expiresAt,
        String reason,
        Integer httpStatus
) {
    boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    YouTubePlaybackPrecheckResult toResult(String videoId) {
        return new YouTubePlaybackPrecheckResult(status, videoId, checkedAt, expiresAt, reason, httpStatus);
    }
}
