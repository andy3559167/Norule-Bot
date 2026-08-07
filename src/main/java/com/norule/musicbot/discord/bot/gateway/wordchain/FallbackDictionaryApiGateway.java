package com.norule.musicbot.discord.bot.gateway.wordchain;

import com.norule.musicbot.domain.wordchain.DictionaryLookupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class FallbackDictionaryApiGateway implements DictionaryApiGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackDictionaryApiGateway.class);

    private final DictionaryApiGateway primary;
    private final DictionaryApiGateway fallback;
    private final boolean fallbackEnabled;

    public FallbackDictionaryApiGateway(
            DictionaryApiGateway primary,
            DictionaryApiGateway fallback,
            boolean fallbackEnabled
    ) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.fallbackEnabled = fallbackEnabled;
    }

    @Override
    public CompletableFuture<DictionaryLookupResult> lookup(String word) {
        return lookupSafely(primary, word).thenCompose(primaryResult -> {
            if (primaryResult == DictionaryLookupResult.FOUND) {
                LOGGER.debug("Dictionary primary found: word={}", word);
                return CompletableFuture.completedFuture(DictionaryLookupResult.FOUND);
            }
            if (!fallbackEnabled) {
                return CompletableFuture.completedFuture(primaryResult);
            }

            LOGGER.debug(
                    "Dictionary primary {}, using fallback: word={}",
                    primaryResult == DictionaryLookupResult.NOT_FOUND ? "not found" : "error",
                    word
            );
            return lookupSafely(fallback, word).thenApply(fallbackResult -> {
                LOGGER.debug("Dictionary fallback result: word={}, result={}", word, fallbackResult);
                return combine(fallbackResult);
            });
        });
    }

    private CompletableFuture<DictionaryLookupResult> lookupSafely(DictionaryApiGateway gateway, String word) {
        try {
            CompletableFuture<DictionaryLookupResult> future = gateway.lookup(word);
            if (future == null) {
                return CompletableFuture.completedFuture(DictionaryLookupResult.API_ERROR);
            }
            return future.handle((result, error) -> error == null && result != null
                    ? result
                    : DictionaryLookupResult.API_ERROR);
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(DictionaryLookupResult.API_ERROR);
        }
    }

    private DictionaryLookupResult combine(DictionaryLookupResult fallbackResult) {
        if (fallbackResult == DictionaryLookupResult.FOUND) {
            return DictionaryLookupResult.FOUND;
        }
        if (fallbackResult == DictionaryLookupResult.NOT_FOUND) {
            return DictionaryLookupResult.NOT_FOUND;
        }
        return DictionaryLookupResult.API_ERROR;
    }
}
