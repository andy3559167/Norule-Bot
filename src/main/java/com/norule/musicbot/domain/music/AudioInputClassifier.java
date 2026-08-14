package com.norule.musicbot.domain.music;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class AudioInputClassifier {
    private static final Pattern EXPLICIT_SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):(.*)$");
    private static final Set<String> URL_SCHEMES = Set.of(
            "http", "https", "ftp", "ftps", "file", "data", "jar", "gopher", "smb", "ldap", "mailto"
    );
    private static final Set<String> DIRECT_AUDIO_EXTENSIONS = Set.of(
            ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac", ".webm"
    );
    private static final Set<String> EXACT_KNOWN_HOSTS = Set.of(
            "soundcloud.com",
            "bandcamp.com",
            "vimeo.com",
            "twitch.tv",
            "beam.pro",
            "getyarn.io",
            "nico.ms",
            "nicovideo.jp",
            "music.yandex.ru",
            "music.yandex.com"
    );

    private final SpotifyResourceParser spotifyParser;

    public AudioInputClassifier() {
        this(new SpotifyResourceParser());
    }

    AudioInputClassifier(SpotifyResourceParser spotifyParser) {
        this.spotifyParser = spotifyParser;
    }

    public enum AudioInputType {
        SEARCH_QUERY,
        YOUTUBE_URL,
        SPOTIFY_TRACK,
        SPOTIFY_ALBUM,
        SPOTIFY_PLAYLIST,
        SPOTIFY_ARTIST,
        SPOTIFY_EPISODE,
        SPOTIFY_SHOW,
        BILIBILI_URL,
        KNOWN_AUDIO_SERVICE_URL,
        DIRECT_HTTP_AUDIO,
        UNSUPPORTED_URL,
        INVALID_URL
    }

    public record Classification(AudioInputType type, String normalizedInput, URI uri) {
        public boolean isUrlLike() {
            return type != AudioInputType.SEARCH_QUERY;
        }
    }

    public Classification classify(String input) {
        String normalized = input == null ? "" : input.trim();
        if (normalized.isBlank()) {
            return new Classification(AudioInputType.SEARCH_QUERY, normalized, null);
        }
        if (normalized.regionMatches(true, 0, "ytsearch:", 0, "ytsearch:".length())) {
            return new Classification(AudioInputType.SEARCH_QUERY, normalized, null);
        }
        Classification spotifyUri = classifySpotifyUri(normalized);
        if (spotifyUri != null) {
            return spotifyUri;
        }
        if (!looksLikeUriInput(normalized)) {
            return new Classification(AudioInputType.SEARCH_QUERY, normalized, null);
        }

        final URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ignored) {
            return new Classification(AudioInputType.INVALID_URL, normalized, null);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return new Classification(AudioInputType.INVALID_URL, normalized, uri);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return new Classification(AudioInputType.INVALID_URL, normalized, uri);
        }

        var spotify = spotifyParser.parse(uri);
        if (spotify.isPresent()) {
            return new Classification(mapSpotifyType(spotify.get().type()), normalized, uri);
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (isYouTubeHost(lowerHost)) {
            return new Classification(AudioInputType.YOUTUBE_URL, normalized, uri);
        }
        if (isBilibiliHost(lowerHost)) {
            return new Classification(AudioInputType.BILIBILI_URL, normalized, uri);
        }
        if (isKnownAudioServiceHost(lowerHost)) {
            return new Classification(AudioInputType.KNOWN_AUDIO_SERVICE_URL, normalized, uri);
        }
        if (hasDirectAudioExtension(uri.getRawPath())) {
            return new Classification(AudioInputType.DIRECT_HTTP_AUDIO, normalized, uri);
        }
        return new Classification(AudioInputType.UNSUPPORTED_URL, normalized, uri);
    }

    public boolean looksLikeUriInput(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String normalized = input.trim();
        var matcher = EXPLICIT_SCHEME.matcher(normalized);
        if (!matcher.matches()) {
            return false;
        }
        String scheme = matcher.group(1).toLowerCase(Locale.ROOT);
        String remainder = matcher.group(2);
        return URL_SCHEMES.contains(scheme) || remainder.startsWith("//");
    }

    private Classification classifySpotifyUri(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("spotify:")) {
            return null;
        }
        String[] parts = input.split(":", 3);
        if (parts.length != 3 || parts[2].isBlank()) {
            return new Classification(AudioInputType.INVALID_URL, input, null);
        }
        AudioInputType type = switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "track" -> AudioInputType.SPOTIFY_TRACK;
            case "album" -> AudioInputType.SPOTIFY_ALBUM;
            case "playlist" -> AudioInputType.SPOTIFY_PLAYLIST;
            case "artist" -> AudioInputType.SPOTIFY_ARTIST;
            case "episode" -> AudioInputType.SPOTIFY_EPISODE;
            case "show" -> AudioInputType.SPOTIFY_SHOW;
            default -> AudioInputType.UNSUPPORTED_URL;
        };
        return new Classification(type, input, null);
    }

    private AudioInputType mapSpotifyType(SpotifyResourceParser.ResourceType type) {
        return switch (type) {
            case TRACK -> AudioInputType.SPOTIFY_TRACK;
            case ALBUM -> AudioInputType.SPOTIFY_ALBUM;
            case PLAYLIST -> AudioInputType.SPOTIFY_PLAYLIST;
            case ARTIST -> AudioInputType.SPOTIFY_ARTIST;
            case EPISODE -> AudioInputType.SPOTIFY_EPISODE;
            case SHOW -> AudioInputType.SPOTIFY_SHOW;
            case UNKNOWN -> AudioInputType.UNSUPPORTED_URL;
        };
    }

    private boolean isYouTubeHost(String host) {
        return "youtu.be".equals(host)
                || "youtube.com".equals(host)
                || host.endsWith(".youtube.com")
                || "youtube-nocookie.com".equals(host)
                || host.endsWith(".youtube-nocookie.com");
    }

    private boolean isKnownAudioServiceHost(String host) {
        if (EXACT_KNOWN_HOSTS.contains(host)) {
            return true;
        }
        return host.endsWith(".soundcloud.com")
                || host.endsWith(".bandcamp.com")
                || host.endsWith(".vimeo.com")
                || host.endsWith(".twitch.tv")
                || host.endsWith(".nicovideo.jp");
    }

    private boolean isBilibiliHost(String host) {
        return "bilibili.com".equals(host)
                || "www.bilibili.com".equals(host)
                || "m.bilibili.com".equals(host)
                || "b23.tv".equals(host)
                || "www.b23.tv".equals(host);
    }

    private boolean hasDirectAudioExtension(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return false;
        }
        String lowerPath = rawPath.toLowerCase(Locale.ROOT);
        return DIRECT_AUDIO_EXTENSIONS.stream().anyMatch(lowerPath::endsWith);
    }
}
