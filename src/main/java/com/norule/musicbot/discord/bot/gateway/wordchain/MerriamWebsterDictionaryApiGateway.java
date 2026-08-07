package com.norule.musicbot.discord.bot.gateway.wordchain;

import com.norule.musicbot.config.BotConfig;
import com.norule.musicbot.domain.wordchain.DictionaryLookupResult;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class MerriamWebsterDictionaryApiGateway implements DictionaryApiGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(MerriamWebsterDictionaryApiGateway.class);

    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final Duration requestTimeout;

    public MerriamWebsterDictionaryApiGateway(
            HttpClient httpClient,
            BotConfig.Dictionary.MerriamWebster config,
            Duration requestTimeout
    ) {
        this(
                httpClient,
                config == null ? BotConfig.Dictionary.MerriamWebster.DEFAULT_ENDPOINT : config.getEndpoint(),
                config == null ? "" : config.getApiKey(),
                requestTimeout
        );
    }

    public MerriamWebsterDictionaryApiGateway(
            HttpClient httpClient,
            String endpoint,
            String apiKey,
            Duration requestTimeout
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.endpoint = normalizeEndpoint(endpoint, BotConfig.Dictionary.MerriamWebster.DEFAULT_ENDPOINT);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.requestTimeout = positiveDuration(
                requestTimeout,
                Duration.ofSeconds(BotConfig.Dictionary.DEFAULT_REQUEST_TIMEOUT_SECONDS)
        );
    }

    @Override
    public CompletableFuture<DictionaryLookupResult> lookup(String word) {
        String safeWord = normalizeWord(word);
        if (safeWord.isBlank()) {
            return CompletableFuture.completedFuture(DictionaryLookupResult.NOT_FOUND);
        }
        if (apiKey.isBlank()) {
            return CompletableFuture.completedFuture(DictionaryLookupResult.API_ERROR);
        }

        HttpRequest request;
        try {
            String encodedWord = URLEncoder.encode(safeWord, StandardCharsets.UTF_8);
            String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            request = HttpRequest.newBuilder(URI.create(endpoint + encodedWord + "?key=" + encodedApiKey))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (RuntimeException error) {
            LOGGER.warn("Merriam-Webster lookup request could not be created: word={}", safeWord);
            return CompletableFuture.completedFuture(DictionaryLookupResult.API_ERROR);
        }

        try {
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(Math.max(1L, requestTimeout.toMillis()), TimeUnit.MILLISECONDS)
                    .handle((response, error) -> resolveResponse(safeWord, response, error));
        } catch (RuntimeException error) {
            LOGGER.warn("Merriam-Webster lookup failed to start: word={}", safeWord);
            return CompletableFuture.completedFuture(DictionaryLookupResult.API_ERROR);
        }
    }

    private DictionaryLookupResult resolveResponse(
            String word,
            HttpResponse<String> response,
            Throwable error
    ) {
        if (error != null || response == null) {
            LOGGER.warn(
                    "Merriam-Webster lookup failed: word={}, cause={}",
                    word,
                    error == null ? "missing-response" : error.getClass().getSimpleName()
            );
            return DictionaryLookupResult.API_ERROR;
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            LOGGER.warn("Merriam-Webster lookup failed: word={}, status={}", word, status);
            return DictionaryLookupResult.API_ERROR;
        }

        try {
            DataArray entries = DataArray.fromJson(response.body());
            if (entries.isEmpty()) {
                return DictionaryLookupResult.NOT_FOUND;
            }
            return entries.isType(0, DataType.OBJECT)
                    ? DictionaryLookupResult.FOUND
                    : DictionaryLookupResult.NOT_FOUND;
        } catch (RuntimeException parseFailure) {
            LOGGER.warn("Merriam-Webster returned invalid JSON: word={}", word);
            return DictionaryLookupResult.API_ERROR;
        }
    }

    private static String normalizeWord(String word) {
        return word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeEndpoint(String endpoint, String defaultEndpoint) {
        String normalized = endpoint == null ? "" : endpoint.trim();
        if (normalized.isBlank()) {
            return defaultEndpoint;
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private static Duration positiveDuration(Duration value, Duration defaultValue) {
        return value == null || value.isZero() || value.isNegative() ? defaultValue : value;
    }
}
