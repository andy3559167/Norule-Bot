package com.norule.musicbot.domain.music;

public record YoutubeClientFailure(
        String clientName,
        YoutubeFailureCategory category,
        Integer httpStatus,
        String exceptionType,
        String safeMessage
) {
    public YoutubeClientFailure {
        clientName = clientName == null || clientName.isBlank() ? "UNKNOWN_CLIENT" : clientName.trim();
        category = category == null ? YoutubeFailureCategory.UNKNOWN : category;
        exceptionType = exceptionType == null || exceptionType.isBlank() ? "-" : exceptionType.trim();
        safeMessage = safeMessage == null || safeMessage.isBlank() ? "-" : safeMessage.trim();
    }
}
