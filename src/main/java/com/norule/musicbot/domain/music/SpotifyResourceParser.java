package com.norule.musicbot.domain.music;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class SpotifyResourceParser {
    private static final Set<String> SPOTIFY_HOSTS = Set.of("open.spotify.com", "www.open.spotify.com");
    private static final Pattern RESOURCE_ID = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern INTERNATIONAL_PREFIX = Pattern.compile("(?i)intl-[a-z]{2}(?:-[a-z]{2})?");

    public enum ResourceType {
        TRACK,
        ALBUM,
        PLAYLIST,
        ARTIST,
        EPISODE,
        SHOW,
        UNKNOWN
    }

    public record SpotifyResource(ResourceType type, String id, URI originalUri) {
    }

    public Optional<SpotifyResource> parse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            return parse(URI.create(input.trim()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public Optional<SpotifyResource> parse(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return Optional.empty();
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme))
                || !SPOTIFY_HOSTS.contains(host)
                || uri.getRawUserInfo() != null
                || !isStandardPort(uri)) {
            return Optional.empty();
        }

        List<String> segments = pathSegments(uri.normalize().getRawPath());
        int resourceIndex = !segments.isEmpty() && INTERNATIONAL_PREFIX.matcher(segments.get(0)).matches() ? 1 : 0;
        if (segments.size() != resourceIndex + 2) {
            return Optional.of(new SpotifyResource(ResourceType.UNKNOWN, "", uri));
        }

        String id = segments.get(resourceIndex + 1);
        if (!RESOURCE_ID.matcher(id).matches()) {
            return Optional.of(new SpotifyResource(ResourceType.UNKNOWN, "", uri));
        }
        ResourceType type = switch (segments.get(resourceIndex).toLowerCase(Locale.ROOT)) {
            case "track" -> ResourceType.TRACK;
            case "album" -> ResourceType.ALBUM;
            case "playlist" -> ResourceType.PLAYLIST;
            case "artist" -> ResourceType.ARTIST;
            case "episode" -> ResourceType.EPISODE;
            case "show" -> ResourceType.SHOW;
            default -> ResourceType.UNKNOWN;
        };
        return Optional.of(new SpotifyResource(type, type == ResourceType.UNKNOWN ? "" : id, uri));
    }

    private List<String> pathSegments(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return List.of();
        }
        return Pattern.compile("/")
                .splitAsStream(rawPath)
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    private boolean isStandardPort(URI uri) {
        int port = uri.getPort();
        return port == -1
                || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
    }
}
