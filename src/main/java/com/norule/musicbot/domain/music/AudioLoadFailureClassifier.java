package com.norule.musicbot.domain.music;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AudioLoadFailureClassifier {
    private static final Pattern HTTP_STATUS = Pattern.compile("(?<!\\d)([45]\\d{2})(?!\\d)");

    public enum Category {
        INVALID_INPUT,
        UNSUPPORTED_SOURCE,
        DNS_FAILURE,
        CONNECT_TIMEOUT,
        READ_TIMEOUT,
        HTTP_FORBIDDEN,
        HTTP_NOT_FOUND,
        HTTP_SERVER_ERROR,
        UNSUPPORTED_FORMAT,
        SOURCE_RATE_LIMITED,
        SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE,
        SPOTIFY_AUTH_FAILED,
        SPOTIFY_RATE_LIMITED,
        SPOTIFY_RESTRICTED_OR_PERSONALIZED,
        SPOTIFY_PLAYLIST_EMPTY,
        TRACK_STUCK,
        TEMPORARY_SOURCE_FAILURE,
        UNKNOWN
    }

    public Category classify(Throwable failure) {
        if (failure == null) {
            return Category.UNKNOWN;
        }
        if (isSpotifyGeneratedPlaylistUnavailable(failure)) {
            return Category.SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE;
        }
        Category messageCategory = Category.UNKNOWN;
        Throwable current = failure;
        while (current != null) {
            String message = normalizedMessage(current);
            if (message.contains("unsafe audio redirect blocked")
                    || message.contains("unsafe audio destination blocked")
                    || message.contains("audio_unsupported_source")) {
                return Category.UNSUPPORTED_SOURCE;
            }
            if (current instanceof UnknownHostException) {
                return Category.DNS_FAILURE;
            }
            if (current instanceof SocketTimeoutException) {
                return message.contains("connect") ? Category.CONNECT_TIMEOUT : Category.READ_TIMEOUT;
            }
            if (current instanceof ConnectException) {
                return Category.CONNECT_TIMEOUT;
            }
            Category currentCategory = classifyMessage(message);
            if (currentCategory != Category.UNKNOWN) {
                messageCategory = currentCategory;
            }
            current = current.getCause();
        }
        return messageCategory;
    }

    public boolean isRecoverable(Throwable failure) {
        return isRecoverable(classify(failure));
    }

    public boolean isRecoverable(Category category) {
        return category == Category.CONNECT_TIMEOUT
                || category == Category.READ_TIMEOUT
                || category == Category.HTTP_SERVER_ERROR
                || category == Category.TRACK_STUCK
                || category == Category.TEMPORARY_SOURCE_FAILURE;
    }

    public boolean isExpectedInputFailure(Category category) {
        return category != null && category != Category.UNKNOWN && category != Category.TRACK_STUCK;
    }

    public String errorKey(Category category) {
        if (category == null) {
            return "AUDIO_UNKNOWN";
        }
        if (category.name().startsWith("SPOTIFY_")) {
            return category.name();
        }
        return "AUDIO_" + category.name();
    }

    private boolean isSpotifyGeneratedPlaylistUnavailable(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = normalizedMessage(current);
            if (message.contains("spotify generated playlists")
                    && message.contains("anonymous token")) {
                return true;
            }
        }
        return false;
    }

    private Category classifyMessage(String message) {
        if (message.isBlank()) {
            return Category.UNKNOWN;
        }
        if (message.contains("track got stuck") || message.contains("track stuck")) {
            return Category.TRACK_STUCK;
        }
        if (message.contains("audio_dns_failure")) {
            return Category.DNS_FAILURE;
        }
        if (message.contains("audio_invalid_input")) {
            return Category.INVALID_INPUT;
        }
        if (message.contains("unknown file format")
                || message.contains("unsupported container")
                || message.contains("unsupported format")) {
            return Category.UNSUPPORTED_FORMAT;
        }
        if (message.contains("too many requests") || message.contains("rate limit") || message.contains(" 429")) {
            return Category.SOURCE_RATE_LIMITED;
        }
        if (message.contains("unknown host") || message.contains("name or service not known")) {
            return Category.DNS_FAILURE;
        }
        if (message.contains("connect timed out") || message.contains("connection timed out")) {
            return Category.CONNECT_TIMEOUT;
        }
        if (message.contains("read timed out") || message.contains("read timeout")) {
            return Category.READ_TIMEOUT;
        }
        Matcher status = HTTP_STATUS.matcher(message);
        while (status.find()) {
            int code = Integer.parseInt(status.group(1));
            if (code == 403) {
                return Category.HTTP_FORBIDDEN;
            }
            if (code == 404) {
                return Category.HTTP_NOT_FOUND;
            }
            if (code == 429) {
                return Category.SOURCE_RATE_LIMITED;
            }
            if (code >= 500) {
                return Category.HTTP_SERVER_ERROR;
            }
        }
        if (message.contains("temporarily unavailable")
                || message.contains("connection reset")
                || message.contains("broken pipe")
                || message.contains("stream closed")) {
            return Category.TEMPORARY_SOURCE_FAILURE;
        }
        return Category.UNKNOWN;
    }

    private String normalizedMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? "" : message.toLowerCase(Locale.ROOT);
    }
}
