package com.norule.musicbot.domain.music;

public record TrackLoadContext(
        String originalInput,
        String resolvedIdentifier,
        String sourceName,
        Long requesterId,
        String requesterName,
        int recoveryAttempts,
        long recoveryResumePosition
) {
    public TrackLoadContext(String originalInput,
                            String resolvedIdentifier,
                            String sourceName,
                            Long requesterId,
                            String requesterName,
                            int recoveryAttempts) {
        this(originalInput, resolvedIdentifier, sourceName, requesterId, requesterName, recoveryAttempts, 0L);
    }

    public TrackLoadContext {
        originalInput = originalInput == null ? "" : originalInput.trim();
        resolvedIdentifier = resolvedIdentifier == null ? "" : resolvedIdentifier.trim();
        sourceName = sourceName == null || sourceName.isBlank() ? "youtube" : sourceName.trim();
        requesterName = requesterName == null ? "" : requesterName.trim();
        recoveryAttempts = Math.max(0, recoveryAttempts);
        recoveryResumePosition = Math.max(0L, recoveryResumePosition);
    }

    public TrackLoadContext withRecoveryAttempt(int attempts, long resumePosition) {
        return new TrackLoadContext(
                originalInput,
                resolvedIdentifier,
                sourceName,
                requesterId,
                requesterName,
                attempts,
                resumePosition
        );
    }

    public TrackLoadContext resetRecovery() {
        return new TrackLoadContext(originalInput, resolvedIdentifier, sourceName, requesterId, requesterName, 0, 0L);
    }
}
