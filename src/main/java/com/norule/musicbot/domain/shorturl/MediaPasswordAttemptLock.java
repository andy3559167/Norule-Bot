package com.norule.musicbot.domain.shorturl;

public record MediaPasswordAttemptLock(
        String shareCode,
        String ipHash,
        int failedAttempts,
        long firstFailureAt,
        long lastFailureAt,
        long nextAllowedAttemptAt,
        long lockedUntil
) {
    public MediaPasswordAttemptLock {
        shareCode = shareCode == null ? "" : shareCode;
        ipHash = ipHash == null ? "" : ipHash;
        failedAttempts = Math.max(0, failedAttempts);
        firstFailureAt = Math.max(0L, firstFailureAt);
        lastFailureAt = Math.max(0L, lastFailureAt);
        nextAllowedAttemptAt = Math.max(0L, nextAllowedAttemptAt);
        lockedUntil = Math.max(0L, lockedUntil);
    }
}
