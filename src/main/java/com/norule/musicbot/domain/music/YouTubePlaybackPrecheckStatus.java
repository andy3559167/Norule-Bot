package com.norule.musicbot.domain.music;

public enum YouTubePlaybackPrecheckStatus {
    SKIPPED,
    OK,
    BLOCKED,
    LAVALINK_UNAVAILABLE,
    TIMEOUT,
    CONFIG_DISABLED,
    INVALID_YOUTUBE_ID,
    UNKNOWN_ERROR
}
