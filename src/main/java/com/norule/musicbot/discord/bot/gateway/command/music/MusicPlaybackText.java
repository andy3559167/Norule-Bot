package com.norule.musicbot.discord.bot.gateway.command.music;

import com.norule.musicbot.domain.music.YoutubePlaybackErrorMapper;
import com.norule.musicbot.i18n.I18nService;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.Objects;
import java.util.function.Supplier;

public final class MusicPlaybackText {
    private final Supplier<I18nService> i18nSupplier;

    public MusicPlaybackText(Supplier<I18nService> i18nSupplier) {
        this.i18nSupplier = Objects.requireNonNull(i18nSupplier, "i18nSupplier");
    }

    public String detectSource(AudioTrack track) {
        String uri = track.getInfo().uri == null ? "" : track.getInfo().uri.toLowerCase();
        if (uri.contains("spotify")) {
            return "spotify";
        }
        if (uri.contains("youtube") || uri.contains("youtu.be")) {
            return "youtube";
        }
        if (uri.contains("soundcloud.com")) {
            return "soundcloud";
        }
        return "url";
    }

    public String mapRepeatLabel(String lang, String mode) {
        String normalized = mode == null ? "OFF" : mode.toUpperCase();
        return switch (normalized) {
            case "SINGLE" -> i18n().t(lang, "music.repeat_single");
            case "ALL" -> i18n().t(lang, "music.repeat_all");
            default -> i18n().t(lang, "music.repeat_off");
        };
    }

    public String mapMusicLoadError(String lang, String rawError) {
        if ("SPOTIFY_RATE_LIMITED".equalsIgnoreCase(rawError)
                || "SPOTIFY_PLAYLIST_COOLDOWN".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.spotify_playlist_rate_limited");
        }
        if ("SPOTIFY_RESTRICTED_OR_PERSONALIZED".equalsIgnoreCase(rawError)
                || "SPOTIFY_PERSONAL_PLAYLIST_UNSUPPORTED".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.spotify_playlist_restricted");
        }
        if ("SPOTIFY_PLAYLIST_EMPTY".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.spotify_playlist_empty");
        }
        if ("SPOTIFY_AUTH_FAILED".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.spotify_playlist_auth_failed");
        }
        if ("SPOTIFY_JAM_UNSUPPORTED".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.spotify_jam_unsupported");
        }
        if ("SPOTIFY_UNSUPPORTED_LINK".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.spotify_unsupported_link");
        }
        if ("SPOTIFY_SHOW_UNSUPPORTED".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.spotify_show_unsupported");
        }
        if ("SPOTIFY_EPISODE_UNSUPPORTED".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.spotify_episode_unsupported");
        }
        if ("YOUTUBE_PRECHECK_BLOCKED".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.youtube_precheck_blocked");
        }
        if ("YOUTUBE_PRECHECK_TIMEOUT".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.youtube_precheck_timeout");
        }
        if ("YOUTUBE_PRECHECK_UNAVAILABLE".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.youtube_precheck_unavailable");
        }
        if ("YOUTUBE_PRECHECK_INVALID".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.youtube_precheck_invalid");
        }
        if ("YOUTUBE_PRECHECK_UNKNOWN".equalsIgnoreCase(rawError)) {
            return i18n().t(lang, "music.youtube_precheck_unknown");
        }
        if (rawError != null && rawError.regionMatches(true, 0, "AUDIO_", 0, "AUDIO_".length())) {
            return i18n().t(lang, switch (rawError.toUpperCase()) {
                case "AUDIO_INVALID_INPUT" -> "music.audio_invalid_input";
                case "AUDIO_UNSUPPORTED_SOURCE" -> "music.audio_unsupported_source";
                case "AUDIO_DIRECT_HTTP_DISABLED" -> "music.audio_direct_http_disabled";
                case "AUDIO_DNS_FAILURE" -> "music.audio_dns_failure";
                case "AUDIO_CONNECT_TIMEOUT" -> "music.audio_connect_timeout";
                case "AUDIO_READ_TIMEOUT" -> "music.audio_read_timeout";
                case "AUDIO_HTTP_FORBIDDEN" -> "music.audio_http_forbidden";
                case "AUDIO_HTTP_NOT_FOUND" -> "music.audio_http_not_found";
                case "AUDIO_HTTP_SERVER_ERROR" -> "music.audio_http_server_error";
                case "AUDIO_UNSUPPORTED_FORMAT" -> "music.audio_unsupported_format";
                case "AUDIO_SOURCE_RATE_LIMITED" -> "music.audio_source_rate_limited";
                case "AUDIO_TRACK_STUCK" -> "music.audio_track_stuck";
                case "AUDIO_TRACK_RECOVERING" -> "music.audio_track_recovering";
                case "AUDIO_TRACK_RECOVERY_EXHAUSTED" -> "music.audio_track_recovery_exhausted";
                case "AUDIO_TRACK_RECOVERY_FAILED" -> "music.audio_track_recovery_failed";
                default -> "music.load_failed_generic";
            });
        }
        return i18n().t(lang, YoutubePlaybackErrorMapper.toMessageKey(rawError));
    }

    private I18nService i18n() {
        return i18nSupplier.get();
    }
}
