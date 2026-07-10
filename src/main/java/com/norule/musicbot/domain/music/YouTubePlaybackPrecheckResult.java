package com.norule.musicbot.domain.music;

import java.time.Instant;

public record YouTubePlaybackPrecheckResult(
        YouTubePlaybackPrecheckStatus status,
        String videoId,
        Instant checkedAt,
        Instant expiresAt,
        String reason,
        Integer httpStatus
) {
    public boolean allowsQueue() {
        return status == YouTubePlaybackPrecheckStatus.OK
                || status == YouTubePlaybackPrecheckStatus.SKIPPED
                || status == YouTubePlaybackPrecheckStatus.CONFIG_DISABLED;
    }
}
