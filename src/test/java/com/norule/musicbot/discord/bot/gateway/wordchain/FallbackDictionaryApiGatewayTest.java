package com.norule.musicbot.discord.bot.gateway.wordchain;

import com.norule.musicbot.domain.wordchain.DictionaryLookupResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FallbackDictionaryApiGatewayTest {

    @Test
    void primaryFoundDoesNotCallFallback() {
        RecordingGateway primary = new RecordingGateway(DictionaryLookupResult.FOUND);
        RecordingGateway fallback = new RecordingGateway(DictionaryLookupResult.FOUND);
        FallbackDictionaryApiGateway gateway = new FallbackDictionaryApiGateway(primary, fallback, true);

        assertEquals(DictionaryLookupResult.FOUND, gateway.lookup("apple").join());
        assertEquals(1, primary.callCount());
        assertEquals(0, fallback.callCount());
    }

    @Test
    void primaryNotFoundCallsFallback() {
        RecordingGateway primary = new RecordingGateway(DictionaryLookupResult.NOT_FOUND);
        RecordingGateway fallback = new RecordingGateway(DictionaryLookupResult.FOUND);
        FallbackDictionaryApiGateway gateway = new FallbackDictionaryApiGateway(primary, fallback, true);

        assertEquals(DictionaryLookupResult.FOUND, gateway.lookup("apple").join());
        assertEquals(1, fallback.callCount());
    }

    @Test
    void primaryErrorCallsFallback() {
        RecordingGateway primary = new RecordingGateway(DictionaryLookupResult.API_ERROR);
        RecordingGateway fallback = new RecordingGateway(DictionaryLookupResult.FOUND);
        FallbackDictionaryApiGateway gateway = new FallbackDictionaryApiGateway(primary, fallback, true);

        assertEquals(DictionaryLookupResult.FOUND, gateway.lookup("apple").join());
        assertEquals(1, fallback.callCount());
    }

    @Test
    void fallbackFoundReturnsFound() {
        FallbackDictionaryApiGateway gateway = gateway(
                DictionaryLookupResult.NOT_FOUND,
                DictionaryLookupResult.FOUND,
                true
        );

        assertEquals(DictionaryLookupResult.FOUND, gateway.lookup("apple").join());
    }

    @Test
    void fallbackNotFoundReturnsNotFound() {
        FallbackDictionaryApiGateway gateway = gateway(
                DictionaryLookupResult.API_ERROR,
                DictionaryLookupResult.NOT_FOUND,
                true
        );

        assertEquals(DictionaryLookupResult.NOT_FOUND, gateway.lookup("apple").join());
    }

    @Test
    void bothProvidersErrorReturnsApiError() {
        FallbackDictionaryApiGateway gateway = gateway(
                DictionaryLookupResult.API_ERROR,
                DictionaryLookupResult.API_ERROR,
                true
        );

        assertEquals(DictionaryLookupResult.API_ERROR, gateway.lookup("apple").join());
    }

    @Test
    void fallbackErrorAfterPrimaryNotFoundReturnsApiError() {
        FallbackDictionaryApiGateway gateway = gateway(
                DictionaryLookupResult.NOT_FOUND,
                DictionaryLookupResult.API_ERROR,
                true
        );

        assertEquals(DictionaryLookupResult.API_ERROR, gateway.lookup("apple").join());
    }

    @Test
    void disabledFallbackReturnsPrimaryResult() {
        RecordingGateway primary = new RecordingGateway(DictionaryLookupResult.NOT_FOUND);
        RecordingGateway fallback = new RecordingGateway(DictionaryLookupResult.FOUND);
        FallbackDictionaryApiGateway gateway = new FallbackDictionaryApiGateway(primary, fallback, false);

        assertEquals(DictionaryLookupResult.NOT_FOUND, gateway.lookup("apple").join());
        assertEquals(0, fallback.callCount());
    }

    private static FallbackDictionaryApiGateway gateway(
            DictionaryLookupResult primary,
            DictionaryLookupResult fallback,
            boolean fallbackEnabled
    ) {
        return new FallbackDictionaryApiGateway(
                new RecordingGateway(primary),
                new RecordingGateway(fallback),
                fallbackEnabled
        );
    }

    private static final class RecordingGateway implements DictionaryApiGateway {
        private final DictionaryLookupResult result;
        private final AtomicInteger calls = new AtomicInteger();

        private RecordingGateway(DictionaryLookupResult result) {
            this.result = result;
        }

        int callCount() {
            return calls.get();
        }

        @Override
        public CompletableFuture<DictionaryLookupResult> lookup(String word) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(result);
        }
    }
}
