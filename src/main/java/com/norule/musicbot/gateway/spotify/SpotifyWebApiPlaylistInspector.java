package com.norule.musicbot.gateway.spotify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.norule.musicbot.config.domain.MusicConfig;
import com.norule.musicbot.domain.music.SpotifyPlaylistInspector;
import com.norule.musicbot.domain.music.SpotifyResourceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SpotifyWebApiPlaylistInspector implements SpotifyPlaylistInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpotifyWebApiPlaylistInspector.class);
    private static final URI TOKEN_URI = URI.create("https://accounts.spotify.com/api/token");
    private static final String API_BASE = "https://api.spotify.com/v1/playlists/";
    private static final String METADATA_FIELDS = "owner(id),public,name,description,items(total)";
    private static final String ITEM_FIELDS = "items(item(id,type,is_playable),track(id,type,is_playable)),total";
    private static final int ITEM_PAGE_LIMIT = 50;
    private static final int MAX_ITEM_PAGES = 200;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final SpotifyResourceParser resourceParser = new SpotifyResourceParser();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpTransport transport;
    private volatile CachedToken cachedToken;

    public SpotifyWebApiPlaylistInspector() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.transport = request -> httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new ApiResponse(response.statusCode(), response.body()));
    }

    SpotifyWebApiPlaylistInspector(HttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public CompletableFuture<Inspection> inspect(String playlistUrl,
                                                 String requestContext,
                                                 MusicConfig.Spotify config) {
        Optional<SpotifyResourceParser.SpotifyResource> parsed = resourceParser.parse(playlistUrl)
                .filter(resource -> resource.type() == SpotifyResourceParser.ResourceType.PLAYLIST);
        if (parsed.isEmpty() || config == null) {
            return CompletableFuture.completedFuture(Inspection.unavailable());
        }

        Credentials credentials = resolveCredentials(config);
        if (!credentials.available()) {
            logInspection(requestContext, "config", parsed.get().id(), null, 0, "MISSING_CLIENT_CREDENTIALS",
                    Outcome.UNAVAILABLE);
            return CompletableFuture.completedFuture(Inspection.unavailable());
        }

        String playlistId = parsed.get().id();
        return accessToken(credentials, requestContext, playlistId)
                .thenCompose(tokenResult -> {
                    if (tokenResult.failure() != null) {
                        return CompletableFuture.completedFuture(tokenResult.failure());
                    }
                    return inspectMetadata(
                            playlistId,
                            tokenResult.token(),
                            normalizeMarket(config.getCountryCode()),
                            requestContext
                    );
                })
                .exceptionally(error -> {
                    LOGGER.warn(
                            "[NoRule] Spotify playlist inspection failed: context={} playlistId={} status=0 reason={} outcome={}",
                            sanitizeForLog(requestContext),
                            playlistId,
                            sanitizeForLog(rootCauseMessage(error)),
                            Outcome.UNAVAILABLE
                    );
                    return Inspection.unavailable();
                });
    }

    private CompletableFuture<TokenResult> accessToken(Credentials credentials,
                                                       String requestContext,
                                                       String playlistId) {
        CachedToken current = cachedToken;
        if (current != null && current.matches(credentials.clientId()) && current.validAt(Instant.now())) {
            return CompletableFuture.completedFuture(TokenResult.success(current.value()));
        }

        String basicCredentials = Base64.getEncoder().encodeToString(
                (credentials.clientId() + ":" + credentials.clientSecret()).getBytes(StandardCharsets.UTF_8)
        );
        HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Basic " + basicCredentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();
        return transport.send(request).thenApply(response -> {
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                try {
                    JsonNode body = objectMapper.readTree(response.body());
                    String token = text(body, "access_token");
                    if (token.isBlank()) {
                        logInspection(requestContext, "token", playlistId, null, response.statusCode(),
                                "TOKEN_RESPONSE_MISSING_ACCESS_TOKEN", Outcome.UNAVAILABLE);
                        return TokenResult.failure(Inspection.unavailable(response.statusCode()));
                    }
                    long expiresIn = Math.max(60L, body.path("expires_in").asLong(3600L));
                    Instant expiresAt = Instant.now().plusSeconds(Math.max(30L, expiresIn - 60L));
                    cachedToken = new CachedToken(credentials.clientId(), token, expiresAt);
                    return TokenResult.success(token);
                } catch (Exception exception) {
                    logInspection(requestContext, "token", playlistId, null, response.statusCode(),
                            "INVALID_TOKEN_RESPONSE", Outcome.UNAVAILABLE);
                    return TokenResult.failure(Inspection.unavailable(response.statusCode()));
                }
            }

            String reason = spotifyErrorReason(response.body());
            Outcome outcome = tokenFailureOutcome(response.statusCode(), reason);
            logInspection(requestContext, "token", playlistId, null, response.statusCode(), reason, outcome);
            return TokenResult.failure(new Inspection(outcome, null, response.statusCode()));
        });
    }

    private CompletableFuture<Inspection> inspectMetadata(String playlistId,
                                                          String accessToken,
                                                          String market,
                                                          String requestContext) {
        URI uri = playlistUri(playlistId, null, METADATA_FIELDS, market);
        return transport.send(bearerGet(uri, accessToken)).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String reason = spotifyErrorReason(response.body());
                Outcome outcome = apiFailureOutcome(response.statusCode(), false);
                invalidateTokenOnUnauthorized(response.statusCode());
                logInspection(requestContext, "metadata", playlistId, null, response.statusCode(), reason, outcome);
                return CompletableFuture.completedFuture(new Inspection(outcome, null, response.statusCode()));
            }

            Metadata metadata;
            try {
                metadata = parseMetadata(response.body());
            } catch (Exception exception) {
                logInspection(requestContext, "metadata", playlistId, null, response.statusCode(),
                        "INVALID_METADATA_RESPONSE", Outcome.UNAVAILABLE);
                return CompletableFuture.completedFuture(Inspection.unavailable(response.statusCode()));
            }
            return inspectItemsPage(playlistId, accessToken, market, requestContext, metadata, 0, 0);
        });
    }

    private CompletableFuture<Inspection> inspectItemsPage(String playlistId,
                                                           String accessToken,
                                                           String market,
                                                           String requestContext,
                                                           Metadata metadata,
                                                           int offset,
                                                           int pageIndex) {
        URI uri = playlistItemsUri(playlistId, market, offset);
        return transport.send(bearerGet(uri, accessToken)).thenCompose(response -> {
            String stage = "items[offset=" + offset + "]";
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String reason = spotifyErrorReason(response.body());
                Outcome outcome = apiFailureOutcome(response.statusCode(), true);
                invalidateTokenOnUnauthorized(response.statusCode());
                logInspection(requestContext, stage, playlistId, metadata, response.statusCode(), reason, outcome);
                return CompletableFuture.completedFuture(new Inspection(outcome, metadata, response.statusCode()));
            }

            try {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode items = root.get("items");
                if (items == null || !items.isArray()) {
                    logInspection(requestContext, stage, playlistId, metadata, response.statusCode(),
                            "ITEMS_FIELD_MISSING", Outcome.SPOTIFY_RESTRICTED_OR_PERSONALIZED);
                    return CompletableFuture.completedFuture(
                            new Inspection(Outcome.SPOTIFY_RESTRICTED_OR_PERSONALIZED, metadata, response.statusCode())
                    );
                }
                if (containsValidTrack(items)) {
                    logInspection(requestContext, stage, playlistId, metadata, response.statusCode(),
                            "OK", Outcome.READABLE);
                    return CompletableFuture.completedFuture(
                            new Inspection(Outcome.READABLE, metadata, response.statusCode())
                    );
                }

                int responseTotal = root.path("total").asInt(-1);
                int effectiveTotal = Math.max(metadata.declaredItemCount(), responseTotal);
                int nextOffset = offset + items.size();
                if (!items.isEmpty() && effectiveTotal > nextOffset && pageIndex + 1 < MAX_ITEM_PAGES) {
                    return inspectItemsPage(
                            playlistId,
                            accessToken,
                            market,
                            requestContext,
                            metadata,
                            nextOffset,
                            pageIndex + 1
                    );
                }
                if (!items.isEmpty() && effectiveTotal > nextOffset) {
                    logInspection(requestContext, stage, playlistId, metadata, response.statusCode(),
                            "ITEM_PAGE_LIMIT_REACHED", Outcome.SPOTIFY_RESTRICTED_OR_PERSONALIZED);
                    return CompletableFuture.completedFuture(
                            new Inspection(Outcome.SPOTIFY_RESTRICTED_OR_PERSONALIZED, metadata, response.statusCode())
                    );
                }

                boolean metadataShowsContent = metadata.declaredItemCount() > 0 || responseTotal > 0;
                boolean confirmedEmpty = offset == 0
                        && items.isEmpty()
                        && !metadataShowsContent
                        && (metadata.declaredItemCount() == 0 || responseTotal == 0);
                Outcome outcome = confirmedEmpty
                        ? Outcome.SPOTIFY_PLAYLIST_EMPTY
                        : Outcome.SPOTIFY_RESTRICTED_OR_PERSONALIZED;
                String reason = outcome == Outcome.SPOTIFY_PLAYLIST_EMPTY
                        ? "PLAYLIST_EMPTY"
                        : "NO_VALID_TRACKS_IN_NON_EMPTY_PLAYLIST";
                logInspection(requestContext, stage, playlistId, metadata, response.statusCode(), reason, outcome);
                return CompletableFuture.completedFuture(new Inspection(outcome, metadata, response.statusCode()));
            } catch (Exception exception) {
                logInspection(requestContext, stage, playlistId, metadata, response.statusCode(),
                        "INVALID_ITEMS_RESPONSE", Outcome.SPOTIFY_RESTRICTED_OR_PERSONALIZED);
                return CompletableFuture.completedFuture(
                        new Inspection(Outcome.SPOTIFY_RESTRICTED_OR_PERSONALIZED, metadata, response.statusCode())
                );
            }
        });
    }

    private Metadata parseMetadata(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String ownerId = text(root.path("owner"), "id");
        JsonNode publicValue = root.get("public");
        Boolean publicPlaylist = publicValue != null && publicValue.isBoolean() ? publicValue.booleanValue() : null;
        int declaredItemCount = root.path("items").path("total").asInt(-1);
        if (declaredItemCount < 0) {
            declaredItemCount = root.path("tracks").path("total").asInt(-1);
        }
        return new Metadata(
                ownerId,
                publicPlaylist,
                text(root, "name"),
                text(root, "description"),
                declaredItemCount
        );
    }

    private boolean containsValidTrack(JsonNode items) {
        for (JsonNode entry : items) {
            JsonNode track = entry.get("item");
            if (track == null || track.isNull() || track.isMissingNode()) {
                track = entry.get("track");
            }
            if (track == null || !track.isObject()) {
                continue;
            }
            String type = text(track, "type");
            String id = text(track, "id");
            boolean playable = !track.has("is_playable") || track.path("is_playable").asBoolean(false);
            if (!id.isBlank() && (type.isBlank() || "track".equalsIgnoreCase(type)) && playable) {
                return true;
            }
        }
        return false;
    }

    private HttpRequest bearerGet(URI uri, String accessToken) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private URI playlistUri(String playlistId, String suffix, String fields, String market) {
        StringBuilder value = new StringBuilder(API_BASE).append(playlistId);
        if (suffix != null && !suffix.isBlank()) {
            value.append('/').append(suffix);
        }
        value.append("?fields=").append(URLEncoder.encode(fields, StandardCharsets.UTF_8));
        if (!market.isBlank()) {
            value.append("&market=").append(URLEncoder.encode(market, StandardCharsets.UTF_8));
        }
        return URI.create(value.toString());
    }

    private URI playlistItemsUri(String playlistId, String market, int offset) {
        StringBuilder value = new StringBuilder(API_BASE)
                .append(playlistId)
                .append("/items?fields=")
                .append(URLEncoder.encode(ITEM_FIELDS, StandardCharsets.UTF_8))
                .append("&limit=")
                .append(ITEM_PAGE_LIMIT)
                .append("&offset=")
                .append(offset);
        if (!market.isBlank()) {
            value.append("&market=").append(URLEncoder.encode(market, StandardCharsets.UTF_8));
        }
        return URI.create(value.toString());
    }

    private Credentials resolveCredentials(MusicConfig.Spotify config) {
        return new Credentials(
                firstNonBlank(System.getenv("SPOTIFY_CLIENT_ID"), config.getClientId()),
                firstNonBlank(System.getenv("SPOTIFY_CLIENT_SECRET"), config.getClientSecret())
        );
    }

    private String normalizeMarket(String value) {
        String market = firstNonBlank(System.getenv("SPOTIFY_COUNTRY_CODE"), value);
        return market != null && market.matches("(?i)[a-z]{2}") ? market.toUpperCase(Locale.ROOT) : "";
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? "" : second.trim();
    }

    private Outcome tokenFailureOutcome(int statusCode, String reason) {
        if (statusCode == 401 || (statusCode == 400 && reason.toLowerCase(Locale.ROOT).contains("invalid_client"))) {
            return Outcome.SPOTIFY_AUTH_FAILED;
        }
        return statusCode == 429 ? Outcome.SPOTIFY_RATE_LIMITED : Outcome.UNAVAILABLE;
    }

    private Outcome apiFailureOutcome(int statusCode, boolean metadataAvailable) {
        if (statusCode == 401) {
            return Outcome.SPOTIFY_AUTH_FAILED;
        }
        if (statusCode == 429) {
            return Outcome.SPOTIFY_RATE_LIMITED;
        }
        if (metadataAvailable && (statusCode == 403 || statusCode == 404)) {
            return Outcome.SPOTIFY_RESTRICTED_OR_PERSONALIZED;
        }
        return Outcome.UNAVAILABLE;
    }

    private void invalidateTokenOnUnauthorized(int statusCode) {
        if (statusCode == 401) {
            cachedToken = null;
        }
    }

    private String spotifyErrorReason(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "" : body);
            JsonNode error = root.get("error");
            if (error != null && error.isObject()) {
                String reason = text(error, "reason");
                String message = text(error, "message");
                if (!reason.isBlank() && !message.isBlank()) {
                    return reason + ": " + message;
                }
                if (!reason.isBlank()) {
                    return reason;
                }
                if (!message.isBlank()) {
                    return message;
                }
            }
            if (error != null && error.isTextual()) {
                String description = text(root, "error_description");
                return description.isBlank() ? error.asText() : error.asText() + ": " + description;
            }
        } catch (Exception ignored) {
            // The raw response is intentionally not logged or returned to Discord.
        }
        return "SPOTIFY_API_ERROR";
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : "";
    }

    private void logInspection(String requestContext,
                               String stage,
                               String playlistId,
                               Metadata metadata,
                               int statusCode,
                               String reason,
                               Outcome outcome) {
        String message = "[NoRule] Spotify playlist inspection: context=" + sanitizeForLog(requestContext)
                + " stage=" + sanitizeForLog(stage)
                + " playlistId=" + sanitizeForLog(playlistId)
                + " ownerId=" + sanitizeForLog(metadata == null ? "-" : metadata.ownerId())
                + " classification=" + (metadata == null ? PlaylistClassification.UNKNOWN : metadata.classification())
                + " public=" + (metadata == null ? "-" : metadata.publicPlaylist())
                + " name=" + sanitizeForLog(metadata == null ? "-" : metadata.name())
                + " declaredItems=" + (metadata == null ? -1 : metadata.declaredItemCount())
                + " status=" + statusCode
                + " reason=" + sanitizeForLog(reason)
                + " outcome=" + outcome;
        if (outcome == Outcome.READABLE || outcome == Outcome.SPOTIFY_PLAYLIST_EMPTY) {
            LOGGER.debug(message);
        } else {
            LOGGER.warn(message);
        }
    }

    private String sanitizeForLog(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value
                .replaceAll("(?i)(authorization\\s*[:=]\\s*)(?:basic|bearer)\\s+[^\\s,;]+", "$1<redacted>")
                .replaceAll("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]+", "$1 <redacted>")
                .replaceAll("(?i)(access[_-]?token|authorization|client_secret)([=: ]+)([^&\\s]+)", "$1$2<redacted>")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return sanitized.length() <= 240 ? sanitized : sanitized.substring(0, 240);
    }

    private String rootCauseMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null) {
            return "UNKNOWN";
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    interface HttpTransport {
        CompletableFuture<ApiResponse> send(HttpRequest request);
    }

    record ApiResponse(int statusCode, String body) {
    }

    private record Credentials(String clientId, String clientSecret) {
        private boolean available() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    private record CachedToken(String clientId, String value, Instant expiresAt) {
        private boolean matches(String expectedClientId) {
            return clientId.equals(expectedClientId);
        }

        private boolean validAt(Instant now) {
            return expiresAt.isAfter(now);
        }
    }

    private record TokenResult(String token, Inspection failure) {
        private static TokenResult success(String token) {
            return new TokenResult(token, null);
        }

        private static TokenResult failure(Inspection inspection) {
            return new TokenResult("", inspection);
        }
    }
}
