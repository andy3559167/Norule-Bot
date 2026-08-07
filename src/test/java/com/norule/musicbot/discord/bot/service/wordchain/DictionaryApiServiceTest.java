package com.norule.musicbot.discord.bot.service.wordchain;

import com.norule.musicbot.discord.bot.gateway.wordchain.DictionaryApiGateway;
import com.norule.musicbot.discord.bot.gateway.wordchain.FallbackDictionaryApiGateway;
import com.norule.musicbot.domain.wordchain.DictionaryLookupResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DictionaryApiServiceTest {

    @Test
    void cachedValidWordSkipsAllProviders() {
        FakeGateway primary = new FakeGateway();
        FakeGateway fallback = new FakeGateway();
        primary.set("apple", DictionaryLookupResult.FOUND);
        DictionaryApiService service = service(primary, fallback);

        assertEquals(DictionaryLookupResult.FOUND, service.lookupWord("apple").join());
        assertEquals(DictionaryLookupResult.FOUND, service.lookupWord("apple").join());
        assertEquals(1, primary.calls("apple"));
        assertEquals(0, fallback.calls("apple"));
    }

    @Test
    void cachedInvalidWordSkipsAllProviders() {
        FakeGateway primary = new FakeGateway();
        FakeGateway fallback = new FakeGateway();
        primary.set("ghostword", DictionaryLookupResult.NOT_FOUND);
        fallback.set("ghostword", DictionaryLookupResult.NOT_FOUND);
        DictionaryApiService service = service(primary, fallback);

        assertEquals(DictionaryLookupResult.NOT_FOUND, service.lookupWord("ghostword").join());
        assertEquals(DictionaryLookupResult.NOT_FOUND, service.lookupWord("ghostword").join());
        assertEquals(1, primary.calls("ghostword"));
        assertEquals(1, fallback.calls("ghostword"));
    }

    @Test
    void apiErrorIsNotCached() {
        FakeGateway primary = new FakeGateway();
        FakeGateway fallback = new FakeGateway();
        primary.set("flaky", DictionaryLookupResult.API_ERROR);
        fallback.set("flaky", DictionaryLookupResult.API_ERROR);
        DictionaryApiService service = service(primary, fallback);

        assertEquals(DictionaryLookupResult.API_ERROR, service.lookupWord("flaky").join());
        fallback.set("flaky", DictionaryLookupResult.FOUND);
        assertEquals(DictionaryLookupResult.FOUND, service.lookupWord("flaky").join());
        assertEquals(2, primary.calls("flaky"));
        assertEquals(2, fallback.calls("flaky"));
    }

    @Test
    void fallbackFoundWordIsCachedAsValid() {
        FakeGateway primary = new FakeGateway();
        FakeGateway fallback = new FakeGateway();
        primary.set("validfallback", DictionaryLookupResult.NOT_FOUND);
        fallback.set("validfallback", DictionaryLookupResult.FOUND);
        DictionaryApiService service = service(primary, fallback);

        assertEquals(DictionaryLookupResult.FOUND, service.lookupWord("validfallback").join());
        assertEquals(DictionaryLookupResult.FOUND, service.lookupWord("validfallback").join());
        assertEquals(1, primary.calls("validfallback"));
        assertEquals(1, fallback.calls("validfallback"));
    }

    @Test
    void bothProvidersNotFoundCachesInvalid() {
        FakeGateway primary = new FakeGateway();
        FakeGateway fallback = new FakeGateway();
        primary.set("missing", DictionaryLookupResult.NOT_FOUND);
        fallback.set("missing", DictionaryLookupResult.NOT_FOUND);
        DictionaryApiService service = service(primary, fallback);

        assertEquals(DictionaryLookupResult.NOT_FOUND, service.lookupWord("missing").join());
        assertEquals(DictionaryLookupResult.NOT_FOUND, service.lookupWord("missing").join());
        assertEquals(1, primary.calls("missing"));
        assertEquals(1, fallback.calls("missing"));
    }

    @Test
    void cachesFoundAndNotFoundButNotApiError() {
        FakeGateway gateway = new FakeGateway();
        gateway.set("apple", DictionaryLookupResult.FOUND);
        gateway.set("ghostword", DictionaryLookupResult.NOT_FOUND);
        gateway.set("flaky", DictionaryLookupResult.API_ERROR);
        DictionaryApiService service = new DictionaryApiService(gateway);

        assertEquals(DictionaryLookupResult.FOUND, service.lookupWord("apple").join());
        assertEquals(DictionaryLookupResult.FOUND, service.lookupWord("apple").join());
        assertEquals(1, gateway.calls("apple"));

        assertEquals(DictionaryLookupResult.NOT_FOUND, service.lookupWord("ghostword").join());
        assertEquals(DictionaryLookupResult.NOT_FOUND, service.lookupWord("ghostword").join());
        assertEquals(1, gateway.calls("ghostword"));

        assertEquals(DictionaryLookupResult.API_ERROR, service.lookupWord("flaky").join());
        gateway.set("flaky", DictionaryLookupResult.FOUND);
        assertEquals(DictionaryLookupResult.FOUND, service.lookupWord("flaky").join());
        assertEquals(2, gateway.calls("flaky"));
    }

    private static DictionaryApiService service(FakeGateway primary, FakeGateway fallback) {
        return new DictionaryApiService(new FallbackDictionaryApiGateway(primary, fallback, true));
    }

    private static final class FakeGateway implements DictionaryApiGateway {
        private final Map<String, DictionaryLookupResult> results = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

        void set(String word, DictionaryLookupResult result) {
            results.put(word, result);
        }

        int calls(String word) {
            return calls.getOrDefault(word, new AtomicInteger(0)).get();
        }

        @Override
        public CompletableFuture<DictionaryLookupResult> lookup(String word) {
            calls.computeIfAbsent(word, ignored -> new AtomicInteger()).incrementAndGet();
            return CompletableFuture.completedFuture(results.getOrDefault(word, DictionaryLookupResult.NOT_FOUND));
        }
    }
}
