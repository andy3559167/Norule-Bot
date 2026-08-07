package com.norule.musicbot.discord.bot.gateway.wordchain;

import com.norule.musicbot.domain.wordchain.DictionaryLookupResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MerriamWebsterDictionaryApiGatewayTest {
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int responseStatus;
    private volatile String responseBody;
    private volatile long responseDelayMillis;

    @BeforeEach
    void startServer() throws IOException {
        responseStatus = 200;
        responseBody = "[]";
        responseDelayMillis = 0L;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/dictionary/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void objectArrayReturnsFound() {
        responseBody = "[{\"meta\":{\"id\":\"example\"}}]";

        assertEquals(DictionaryLookupResult.FOUND, gateway().lookup("example").join());
    }

    @Test
    void multipleEntryObjectsReturnFound() {
        responseBody = "[{\"meta\":{\"id\":\"one\"}},{\"meta\":{\"id\":\"two\"}}]";

        assertEquals(DictionaryLookupResult.FOUND, gateway().lookup("example").join());
    }

    @Test
    void suggestionStringArrayReturnsNotFound() {
        responseBody = "[\"example\",\"examples\",\"exemplar\"]";

        assertEquals(DictionaryLookupResult.NOT_FOUND, gateway().lookup("exampl").join());
    }

    @Test
    void emptyArrayReturnsNotFound() {
        responseBody = "[]";

        assertEquals(DictionaryLookupResult.NOT_FOUND, gateway().lookup("missing").join());
    }

    @Test
    void invalidJsonReturnsApiError() {
        responseBody = "not-json";

        assertEquals(DictionaryLookupResult.API_ERROR, gateway().lookup("example").join());
    }

    @Test
    void http401ReturnsApiError() {
        assertStatusReturnsApiError(401);
    }

    @Test
    void http403ReturnsApiError() {
        assertStatusReturnsApiError(403);
    }

    @Test
    void http429ReturnsApiError() {
        assertStatusReturnsApiError(429);
    }

    @Test
    void http500ReturnsApiError() {
        assertStatusReturnsApiError(500);
    }

    @Test
    void timeoutReturnsApiError() {
        responseBody = "[{\"meta\":{\"id\":\"example\"}}]";
        responseDelayMillis = 300L;

        assertEquals(
                DictionaryLookupResult.API_ERROR,
                gateway(Duration.ofMillis(50)).lookup("example").join()
        );
    }

    @Test
    void blankWordReturnsNotFound() {
        assertEquals(DictionaryLookupResult.NOT_FOUND, gateway().lookup("  ").join());
        assertEquals(0, calls.get());
    }

    private void assertStatusReturnsApiError(int status) {
        responseStatus = status;
        responseBody = "{}";
        assertEquals(DictionaryLookupResult.API_ERROR, gateway().lookup("example").join());
    }

    private MerriamWebsterDictionaryApiGateway gateway() {
        return gateway(Duration.ofSeconds(2));
    }

    private MerriamWebsterDictionaryApiGateway gateway(Duration timeout) {
        return new MerriamWebsterDictionaryApiGateway(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                endpoint(),
                "test-api-key",
                timeout
        );
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/dictionary/";
    }

    private void handle(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        if (responseDelayMillis > 0L) {
            try {
                Thread.sleep(responseDelayMillis);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
        } catch (IOException ignored) {
            // The client can close the exchange first in the timeout test.
        }
    }
}
