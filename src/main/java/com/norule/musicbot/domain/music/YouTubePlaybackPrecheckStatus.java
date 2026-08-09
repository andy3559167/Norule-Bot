package com.norule.musicbot.domain.music;

public enum YouTubePlaybackPrecheckStatus {
    SKIPPED,
    OK,
    BLOCKED,
    TEMPORARY_FAILURE,
    AUTH_REQUIRED,
    PERMANENT_FAILURE,
    LAVALINK_UNAVAILABLE,
    TIMEOUT,
    CONFIG_DISABLED,
    INVALID_YOUTUBE_ID,
    UNKNOWN_ERROR
}
