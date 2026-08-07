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

class FreeDictionaryApiGatewayTest {
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
    void objectEntryArrayReturnsFound() {
        responseBody = "[{\"word\":\"apple\",\"meanings\":[]}]";

        assertEquals(DictionaryLookupResult.FOUND, gateway().lookup("apple").join());
    }

    @Test
    void emptyArrayReturnsNotFound() {
        assertEquals(DictionaryLookupResult.NOT_FOUND, gateway().lookup("missing").join());
    }

    @Test
    void http404ReturnsNotFound() {
        responseStatus = 404;
        responseBody = "{}";

        assertEquals(DictionaryLookupResult.NOT_FOUND, gateway().lookup("missing").join());
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
    void invalidJsonReturnsApiError() {
        responseBody = "not-json";

        assertEquals(DictionaryLookupResult.API_ERROR, gateway().lookup("apple").join());
    }

    @Test
    void timeoutReturnsApiError() {
        responseBody = "[{\"word\":\"apple\"}]";
        responseDelayMillis = 300L;

        assertEquals(
                DictionaryLookupResult.API_ERROR,
                gateway(Duration.ofMillis(50)).lookup("apple").join()
        );
    }

    @Test
    void blankWordReturnsNotFoundWithoutRequest() {
        assertEquals(DictionaryLookupResult.NOT_FOUND, gateway().lookup(" ").join());
        assertEquals(0, calls.get());
    }

    private void assertStatusReturnsApiError(int status) {
        responseStatus = status;
        responseBody = "{}";
        assertEquals(DictionaryLookupResult.API_ERROR, gateway().lookup("apple").join());
    }

    private FreeDictionaryApiGateway gateway() {
        return gateway(Duration.ofSeconds(2));
    }

    private FreeDictionaryApiGateway gateway(Duration timeout) {
        return new FreeDictionaryApiGateway(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                endpoint(),
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
